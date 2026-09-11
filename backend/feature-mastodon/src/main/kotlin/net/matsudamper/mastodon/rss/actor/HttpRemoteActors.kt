package net.matsudamper.mastodon.rss.actor

import java.io.Closeable
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.ktor.v3_0.KtorClientTelemetry
import net.matsudamper.mastodon.rss.activitypub.ActivityPubContentTypes
import net.matsudamper.mastodon.rss.crypto.RsaKeys
import net.matsudamper.mastodon.rss.httpsignature.PublicKeyLookup
import net.matsudamper.mastodon.rss.httpsignature.SignatureKey
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.webfinger.WebFingerLink
import org.slf4j.LoggerFactory

/**
 * 相手のアクター文書を実際に GET して、公開鍵と inbox を取る。
 *
 * ActivityPub では鍵も配信先も「アクター文書の中に入っている」しか配布方法が無いので、
 * 知らない相手から署名付きのリクエストが来たら、その場で相手のサーバーに取りに行くことになる。
 *
 * 取得先は相手が `keyId` や `actor` として指定してきた URL で、こちらが選べない。
 * つまり任意の URL に GET させられる口でもあるため、次を守る。
 *
 * - https のみ。平文で取った鍵は途中で差し替えられる
 * - リダイレクトで別ホストに移ったら捨てる。移った先のサーバーが
 *   他人のアクターの鍵を名乗れてしまう
 * - 文書に書かれた `owner` と `inbox` は、取得先と同じホストであること。
 *   他所のホストのものだと言い張るものを信じない
 * - 大きすぎる応答は読まない
 *
 * 取得結果は [ExpiringCache] を通してキャッシュし、TTL の間は GET しない。
 * フォローや投稿のたびに毎回相手のサーバーへ取りに行くのは、相手に対しても
 * 自分の inbox 処理に対しても無駄が大きいため。取得に失敗した場合はキャッシュしない。
 * 相手のサーバーが一時的に落ちているだけなら、次の呼び出しで取り直せるようにする。
 */
class HttpRemoteActors(
    openTelemetry: OpenTelemetry? = null,
    private val client: HttpClient = defaultClient(openTelemetry),
) : RemoteActors,
    Closeable {
    private val logger = LoggerFactory.getLogger(HttpRemoteActors::class.java)

    /**
     * アクター文書のキャッシュ。鍵と inbox を別々に持たないのは、
     * どちらも同じ 1 つの文書から読むものだから。
     */
    private val documents: ExpiringCache<String, RemoteActorDocument> = createExpiringCache()

    override suspend fun find(keyId: String): PublicKeyLookup {
        val url = parseHttpsUrl(keyId) ?: return PublicKeyLookup.Unavailable

        val document =
            when (val fetched = fetch(keyId, url)) {
                is DocumentFetch.Found -> fetched.document

                // 相手が「もう無い」と答えた場合だけ、消えたものとして返す。
                // 落ちているだけの相手を消えた扱いにすると、後から生き返る
                DocumentFetch.Gone -> return PublicKeyLookup.Gone

                DocumentFetch.Unavailable -> return PublicKeyLookup.Unavailable
            }

        val publicKey = document.publicKey ?: return PublicKeyLookup.Unavailable

        val keyOwnerActorId = publicKey.owner ?: document.id ?: return PublicKeyLookup.Unavailable
        if (!isSameHost(keyOwnerActorId, url)) return PublicKeyLookup.Unavailable

        val decodedPublicKey =
            runCatching { RsaKeys.decodePublicKeyPem(publicKey.publicKeyPem) }
                .getOrNull() ?: return PublicKeyLookup.Unavailable

        return PublicKeyLookup.Found(
            SignatureKey(keyId = keyId, owner = keyOwnerActorId, publicKey = decodedPublicKey),
        )
    }

    override suspend fun findActor(actorId: String): RemoteActor? {
        val url = parseHttpsUrl(actorId) ?: return null
        val document = (fetch(actorId, url) as? DocumentFetch.Found)?.document ?: return null
        val preferredUsername = document.preferredUsername.asString()?.takeIf { isDisplayableUsername(it) }

        // 宛先はこちらが POST しに行く先になる。アクターと同じホストに限ることで、
        // 相手が自分の文書に書いた URL でこちらから他所へ POST させる形を塞ぐ
        val inbox = document.inbox?.takeIf { isDeliverable(it, url) } ?: return null

        val publicKeyPem = document.publicKey?.publicKeyPem ?: return null

        return RemoteActor(
            actorId = actorId,
            inbox = inbox,
            // 無いのが普通なので、条件を満たさないものは落として先へ進む。
            // sharedInbox が無くても inbox に 1 通ずつ送れば配信自体はできる
            sharedInbox = document.endpoints?.sharedInbox?.takeIf { isDeliverable(it, url) },
            publicKeyPem = publicKeyPem,
            // 管理画面から人が開くリンクになる。https で、アクターと同じホストのものに限る。
            // 他所のホストを指せると、フォローするだけでこちらの画面に任意のリンクを載せられる
            profileUrl = document.url.asString()?.takeIf { parseHttpsUrl(it) != null && isSameHost(it, url) },
            // acct が無くてもフォローは成立する。inbox の応答を待たせないよう短く切る
            acct = preferredUsername?.let { name ->
                withTimeoutOrNull(ACCT_TIMEOUT_MILLIS) {
                    resolveAcct(actorId = actorId, actorUrl = url, preferredUsername = name)
                }
            },
        )
    }

    /**
     * acct を WebFinger で確定させる。
     *
     * `preferredUsername` とアクターのホストを繋げただけの acct は、Mastodon の
     * `WEB_DOMAIN` と `LOCAL_DOMAIN` を分けている相手（アクターは前者、acct は後者）では
     * 検索窓で解決しない。正しい acct はアクターのホストの WebFinger が返す
     * `subject` にしかないので、フォローを受けた時点で引く。
     *
     * `subject` は 2 つ確かめてから信じる。
     *
     * - `links` の `self` がこのアクターを指していること。同じホストの別の
     *   アカウントの名前を名乗られないため
     * - `subject` のホストがアクターのホストと違う場合は、名乗られたホストの
     *   WebFinger にも同じことを言わせる。確かめないと、リンク先はこちらのホスト、
     *   名乗りは他所のホスト、という表示を相手が作れる。委譲は正しくこの形になる
     */
    private suspend fun resolveAcct(
        actorId: String,
        actorUrl: Url,
        preferredUsername: String,
    ): String? {
        // 既定でないポートで動いている相手は、ポートまで含めないと別の接続先を指す
        val authority =
            if (actorUrl.port == actorUrl.protocol.defaultPort) actorUrl.host else "${actorUrl.host}:${actorUrl.port}"

        val document =
            fetchWebFinger(
                authority = authority,
                resource = "$ACCT_SCHEME$preferredUsername@$authority",
                allowedHosts = setOf(actorUrl.host),
                requiredPort = actorUrl.port,
            ) ?: return null

        val acct = document.acctPointingTo(actorId) ?: run {
            logger.info("WebFinger の応答がこのアクターを指していない: $actorId")
            return null
        }

        if (acct.host.equals(actorUrl.host, ignoreCase = true)) return acct.text

        // 名乗られたホスト側の WebFinger。LOCAL_DOMAIN 側はアクターのホストへ
        // リダイレクトするのが普通なので、移った先はどちらでもよい
        val delegated =
            fetchWebFinger(
                authority = acct.host,
                resource = "$ACCT_SCHEME${acct.name}@${acct.host}",
                allowedHosts = setOf(acct.host, actorUrl.host),
                requiredPort = null,
            ) ?: return null

        val confirmed = delegated.acctPointingTo(actorId)
        if (confirmed?.text != acct.text) {
            logger.info("acct のホストが名乗りを裏付けない: $actorId acct=${acct.text}")
            return null
        }

        return acct.text
    }

    /**
     * WebFinger を 1 回引く。読めなければ null。
     *
     * @param allowedHosts リダイレクトで移ってよいホスト。ここに無いホストが返した
     *   ものは、そのホストが他人の acct を名乗れることになるので捨てる
     * @param requiredPort 移った先に許すポート。null なら問わない
     */
    private suspend fun fetchWebFinger(
        authority: String,
        resource: String,
        allowedHosts: Set<String>,
        requiredPort: Int?,
    ): RemoteWebFingerDocument? {
        val response =
            runCatching {
                client.get("https://$authority/.well-known/webfinger") {
                    parameter("resource", resource)
                    // JRD だけを返すサーバーに application/json だけで問い合わせると 406 になる
                    header(
                        HttpHeaders.Accept,
                        "${ActivityPubContentTypes.JrdJson}, ${ContentType.Application.Json}",
                    )
                }
            }.getOrNull()

        if (response == null) {
            logger.info("WebFinger を引けなかった: $resource")
            return null
        }

        if (!response.status.isSuccess()) {
            logger.info("WebFinger が失敗を返した: $resource status=${response.status.value}")
            return null
        }

        // リダイレクトで別の宛先に移っていたら、そこが他人の acct を名乗れる。
        // ポートを落とすと別の接続先になるのと同じ理由で、scheme とポートまで見る
        val finalUrl = response.request.url
        val movedAway =
            finalUrl.protocol != URLProtocol.HTTPS ||
                allowedHosts.none { finalUrl.host.equals(it, ignoreCase = true) } ||
                (requiredPort != null && finalUrl.port != requiredPort)
        if (movedAway) {
            logger.info("WebFinger が別の宛先へ移った: $resource 移った先=${finalUrl.host}")
            return null
        }

        val body = runCatching { response.bodyAsText() }.getOrNull() ?: return null
        if (body.length > MAX_BODY_CHARS) {
            logger.info("WebFinger の応答が大きすぎる: $resource")
            return null
        }

        return runCatching { AppJson.decodeFromString(RemoteWebFingerDocument.serializer(), body) }
            .getOrNull()
            ?: run {
                logger.info("WebFinger の応答を読めなかった: $resource")
                null
            }
    }

    /**
     * `self` が [actorId] を指しているときだけ、`subject` を acct として読む
     */
    private fun RemoteWebFingerDocument.acctPointingTo(actorId: String): ResolvedAcct? {
        val pointsBackToActor = links.any { it.rel == WebFingerLink.REL_SELF && it.href == actorId }
        if (!pointsBackToActor) return null

        val acctSubject = subject?.removePrefix(ACCT_SCHEME) ?: return null
        val name = acctSubject.substringBefore('@', missingDelimiterValue = "")
        val host = acctSubject.substringAfter('@', missingDelimiterValue = "")
        if (!isDisplayableUsername(name) || !isAcctHost(host)) return null

        return ResolvedAcct(name = name, host = host)
    }

    /**
     * 文字列として読めるものだけ拾う。型が違うものは無かったことにする
     */
    private fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    /**
     * acct の名前として出してよいか。
     *
     * acct は管理画面でリンクの表示文字列になる。`@` や空白を通すと、リンク先とは
     * 別のホストを名乗る表示を相手が作れる。プロフィールの URL を同じホストに
     * 絞っているのと同じ理由で、名前の側も相手の言い分をそのまま出さない。
     */
    private fun isDisplayableUsername(raw: String): Boolean =
        raw.isNotEmpty() &&
            raw.length <= MAX_USERNAME_LENGTH &&
            raw.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '.' || it == '-' }

    /**
     * acct のホストとして出してよいか。
     *
     * ホストは相手のサーバーが決めるので、こちらから形を決めきれない。
     * acct の形（`@name@host`）を崩す文字と、表示を壊す文字だけを弾く。
     * 既定でないポートで動いている相手は `host:port` を返してくるので、`:` は通す。
     */
    private fun isAcctHost(raw: String): Boolean =
        raw.isNotEmpty() &&
            raw.length <= MAX_HOST_LENGTH &&
            raw.none { it == '@' || it == '/' || it.isWhitespace() || it.isISOControl() }

    /**
     * POST しに行ってよい宛先か。https で、取得先と同じホストであること
     */
    private fun isDeliverable(
        raw: String,
        actorUrl: Url,
    ): Boolean = parseHttpsUrl(raw) != null && isSameHost(raw, actorUrl)

    override fun close() {
        client.close()
    }

    /**
     * アクター文書を取る。取得先の URL を [requestUrl] として渡すのは、
     * 取れた文書の中身を突き合わせる基準がその URL のホストだから。
     */
    private suspend fun fetch(
        rawUrl: String,
        requestUrl: Url,
    ): DocumentFetch {
        // `keyId` はアクター id にフラグメントを付けたもので、フラグメントはサーバーに
        // 送られない。落としてから引くと、署名の検証で取った文書を
        // `Accept` の宛先を決めるときにも使える
        val cacheKey = rawUrl.substringBefore('#')
        documents.get(cacheKey)?.let { return DocumentFetch.Found(it) }

        val response =
            runCatching {
                client.get(rawUrl) {
                    header(HttpHeaders.Accept, ActivityPubContentTypes.ActivityJson.toString())
                }
            }.getOrNull() ?: return DocumentFetch.Unavailable

        // リダイレクトを追った結果、別のホストに移っていたら信用しない。
        // status を見る前に確かめるのは、消えたかどうかを答えてよいのは
        // そのアクターのホストだけだから
        val fetchedFrom = response.request.url.host
        if (!fetchedFrom.equals(requestUrl.host, ignoreCase = true)) return DocumentFetch.Unavailable

        // 消えたと見なすのは 410 だけ。Mastodon は削除済みのアカウントにこれを返す。
        // 404 は消したのか置き場所が変わったのかを区別できず、
        // 一時的なルーティングの不調でも返るので、分からないものとして扱う
        if (response.status == HttpStatusCode.Gone) return DocumentFetch.Gone
        if (!response.status.isSuccess()) return DocumentFetch.Unavailable

        val body = runCatching { response.bodyAsText() }.getOrNull() ?: return DocumentFetch.Unavailable
        if (body.length > MAX_BODY_CHARS) return DocumentFetch.Unavailable

        val document =
            runCatching { AppJson.decodeFromString(RemoteActorDocument.serializer(), body) }
                .getOrNull() ?: return DocumentFetch.Unavailable

        documents.put(key = cacheKey, value = document, ttlMillis = CACHE_TTL_MILLIS)
        return DocumentFetch.Found(document)
    }

    /**
     * [fetch] の結果。取れなかった理由のうち「もう無い」だけは区別する
     */
    private sealed interface DocumentFetch {
        data class Found(
            val document: RemoteActorDocument,
        ) : DocumentFetch

        data object Gone : DocumentFetch

        data object Unavailable : DocumentFetch
    }

    private fun parseHttpsUrl(raw: String): Url? =
        runCatching { Url(raw) }
            .getOrNull()
            ?.takeIf { it.protocol == URLProtocol.HTTPS }

    /**
     * ポートまで見る。ホストが同じでも別のポートは別の接続先で、
     * 配信先として通すと相手が指した別のサーバーに POST することになる
     */
    private fun isSameHost(
        raw: String,
        expected: Url,
    ): Boolean {
        val url = runCatching { Url(raw) }.getOrNull() ?: return false
        return url.host.equals(expected.host, ignoreCase = true) && url.port == expected.port
    }

    private companion object {
        /**
         * acct の確定に使ってよい時間。アクター文書の取得と `Accept` の送信に
         * 積み上がるので短く切る。ここで諦めても「未取得」になるだけ
         */
        const val ACCT_TIMEOUT_MILLIS = 3_000L

        /**
         * `subject` に付く scheme。`acct:alice@example.com` の形で返ってくる
         */
        const val ACCT_SCHEME = "acct:"

        /**
         * acct に出すホストの長さの上限。DNS 名の上限に合わせる
         */
        const val MAX_HOST_LENGTH = 253

        /**
         * acct に出す名前の長さの上限。Mastodon は 30 文字までだが、
         * 他の実装まで同じとは限らないので緩めに取る
         */
        const val MAX_USERNAME_LENGTH = 64

        /**
         * 読み込む応答の上限。アクター文書は鍵を含めても数 KB にしかならない。
         * 相手のサーバーが延々と送り続けてくる場合は、これと下のタイムアウトで止める。
         */
        const val MAX_BODY_CHARS = 64 * 1024

        /**
         * キャッシュの有効期間。長すぎると相手が鍵をローテーションしたときに
         * 検証が通らない期間が延びる。短すぎるとキャッシュの意味が薄くなる。
         * 1 時間なら、鍵のローテーションは頻度の高い運用ではないので実害は小さい
         */
        const val CACHE_TTL_MILLIS = 60 * 60 * 1000L

        fun defaultClient(openTelemetry: OpenTelemetry? = null): HttpClient =
            HttpClient(CIO) {
                if (openTelemetry != null) {
                    install(KtorClientTelemetry) {
                        setOpenTelemetry(openTelemetry)
                    }
                }
                // 相手のサーバーが応答しないままだと inbox の処理が詰まる。
                // フォロー 1 件のために長く待つ意味は無いので短く切る
                install(HttpTimeout) {
                    connectTimeoutMillis = 5_000
                    requestTimeoutMillis = 10_000
                    socketTimeoutMillis = 10_000
                }
                // 404 や 500 を例外にせず、こちらで status を見て判断する
                expectSuccess = false
            }
    }
}

/**
 * 相手のアクター文書のうち、こちらが見る部分だけ。
 *
 * こちらが返す [net.matsudamper.mastodon.rss.activitypub.Actor] を使い回さないのは、
 * あちらが「返すときに必ず入れるもの」を必須にしているため。相手の実装が
 * `following` を省略しただけで鍵が読めなくなるのは筋が悪い。
 */
@Serializable
private data class RemoteActorDocument(
    @SerialName("id")
    val id: String? = null,
    @SerialName("inbox")
    val inbox: String? = null,
    /**
     * ActivityStreams では文字列のほかに `Link` オブジェクトや配列も取り得る。
     * [String] で受けると、型が違うだけで文書全体のデコードに失敗して
     * 署名の鍵まで読めなくなるので、読める形のときだけ拾う
     */
    @SerialName("url")
    val url: JsonElement? = null,
    @SerialName("preferredUsername")
    val preferredUsername: JsonElement? = null,
    @SerialName("publicKey")
    val publicKey: RemoteActorPublicKey? = null,
    @SerialName("endpoints")
    val endpoints: RemoteActorEndpoints? = null,
)

/**
 * WebFinger から読めた acct。
 *
 * @param text 画面に出す `@name@host` の形
 */
private data class ResolvedAcct(
    val name: String,
    val host: String,
) {
    val text: String = "@$name@$host"
}

/**
 * 相手のホストの WebFinger の応答のうち、こちらが見る部分だけ。
 *
 * こちらが返す [net.matsudamper.mastodon.rss.webfinger.WebFingerResponse] を
 * 使い回さないのは、あちらが「返すときに必ず入れるもの」を必須にしているため。
 * 相手が `links` を省略しただけで読めなくなるのは筋が悪い。
 */
@Serializable
private data class RemoteWebFingerDocument(
    @SerialName("subject")
    val subject: String? = null,
    @SerialName("links")
    val links: List<RemoteWebFingerLink> = listOf(),
)

@Serializable
private data class RemoteWebFingerLink(
    @SerialName("rel")
    val rel: String? = null,
    @SerialName("href")
    val href: String? = null,
)

/**
 * `endpoints` の中身。`sharedInbox` はここにしか無い。
 *
 * 同じインスタンスに複数のフォロワーがいる場合、1 人ずつ inbox に送る代わりに
 * ここへ 1 回送れば済む。
 */
@Serializable
private data class RemoteActorEndpoints(
    @SerialName("sharedInbox")
    val sharedInbox: String? = null,
)

@Serializable
private data class RemoteActorPublicKey(
    @SerialName("id")
    val id: String? = null,
    @SerialName("owner")
    val owner: String? = null,
    @SerialName("publicKeyPem")
    val publicKeyPem: String,
)
