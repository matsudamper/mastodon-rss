package net.matsudamper.mastodon.rss.actor

import java.io.Closeable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.ktor.v3_0.KtorClientTelemetry
import net.matsudamper.mastodon.rss.activitypub.ActivityPubContentTypes
import net.matsudamper.mastodon.rss.crypto.RsaKeys
import net.matsudamper.mastodon.rss.httpsignature.SignatureKey
import net.matsudamper.mastodon.rss.json.AppJson

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
    /**
     * アクター文書のキャッシュ。鍵と inbox を別々に持たないのは、
     * どちらも同じ 1 つの文書から読むものだから。
     */
    private val documents: ExpiringCache<String, RemoteActorDocument> = createExpiringCache()

    override suspend fun find(keyId: String): SignatureKey? {
        val url = parseHttpsUrl(keyId) ?: return null
        val document = fetch(keyId, url) ?: return null

        val publicKey = document.publicKey ?: return null

        val keyOwnerActorId = publicKey.owner ?: document.id ?: return null
        if (!isSameHost(keyOwnerActorId, url)) return null

        val decodedPublicKey =
            runCatching { RsaKeys.decodePublicKeyPem(publicKey.publicKeyPem) }
                .getOrNull() ?: return null

        return SignatureKey(keyId = keyId, owner = keyOwnerActorId, publicKey = decodedPublicKey)
    }

    override suspend fun findActor(actorId: String): RemoteActor? {
        val url = parseHttpsUrl(actorId) ?: return null
        val document = fetch(actorId, url) ?: return null

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
            preferredUsername = document.preferredUsername.asString()?.takeIf { isDisplayableUsername(it) },
        )
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
    ): RemoteActorDocument? {
        // `keyId` はアクター id にフラグメントを付けたもので、フラグメントはサーバーに
        // 送られない。落としてから引くと、署名の検証で取った文書を
        // `Accept` の宛先を決めるときにも使える
        val cacheKey = rawUrl.substringBefore('#')
        documents.get(cacheKey)?.let { return it }

        val response =
            runCatching {
                client.get(rawUrl) {
                    header(HttpHeaders.Accept, ActivityPubContentTypes.ActivityJson.toString())
                }
            }.getOrNull() ?: return null

        if (!response.status.isSuccess()) return null

        // リダイレクトを追った結果、別のホストに移っていたら信用しない
        val fetchedFrom = response.request.url.host
        if (!fetchedFrom.equals(requestUrl.host, ignoreCase = true)) return null

        val body = runCatching { response.bodyAsText() }.getOrNull() ?: return null
        if (body.length > MAX_BODY_CHARS) return null

        val document =
            runCatching { AppJson.decodeFromString(RemoteActorDocument.serializer(), body) }
                .getOrNull() ?: return null

        documents.put(key = cacheKey, value = document, ttlMillis = CACHE_TTL_MILLIS)
        return document
    }

    private fun parseHttpsUrl(raw: String): Url? =
        runCatching { Url(raw) }
            .getOrNull()
            ?.takeIf { it.protocol == URLProtocol.HTTPS }

    private fun isSameHost(
        raw: String,
        expected: Url,
    ): Boolean {
        val host = runCatching { Url(raw) }.getOrNull()?.host ?: return false
        return host.equals(expected.host, ignoreCase = true)
    }

    private companion object {
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
