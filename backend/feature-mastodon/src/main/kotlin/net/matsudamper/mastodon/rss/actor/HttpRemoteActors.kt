package net.matsudamper.mastodon.rss.actor

import java.io.Closeable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
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
 *
 * 覚えている鍵が古くなったかどうかは期限では分からないので、期限は鍵と関係なく決める。
 * 古い鍵に気付けるのは署名の検証が失敗したときだけで、そこからの取り直しは [refresh]。
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
    private val documents: ExpiringCache<String, RemoteActorDocument> = createExpiringCache(MAX_CACHED_ACTORS)

    /**
     * 直前に相手のサーバーへ取りに行ったアクター。[refresh] の間隔を空けるのに使う
     */
    private val recentlyFetched: ExpiringCache<String, Unit> = createExpiringCache(MAX_CACHED_ACTORS)

    override suspend fun find(keyId: String): PublicKeyLookup {
        val url = parseHttpsUrl(keyId) ?: return PublicKeyLookup.Unavailable

        return publicKeyOf(keyId, url, fetch(rawUrl = keyId, requestUrl = url, useCache = true))
    }

    /**
     * 覚えているものを使わずに引き直す。
     *
     * 覚えている文書を先に捨てないのは、取り直しに失敗したときに元の文書まで
     * 失うため。通らない署名を 1 通投げ込むだけで、相手のサーバーが落ちている間
     * その相手の正当なアクティビティを全部落とせる状態になる。取り直せなければ
     * 覚えている文書がそのまま返り、[find] と同じ結果になる。
     */
    override suspend fun refresh(keyId: String): PublicKeyLookup {
        val url = parseHttpsUrl(keyId) ?: return PublicKeyLookup.Unavailable

        return publicKeyOf(keyId, url, fetch(rawUrl = keyId, requestUrl = url, useCache = false))
    }

    /**
     * 取れた文書から検証に使う鍵を読む。
     *
     * @param requestUrl 取得先。文書が名乗る鍵の持ち主が、取得先と同じホストか確かめる
     */
    private fun publicKeyOf(
        keyId: String,
        requestUrl: Url,
        fetched: DocumentFetch,
    ): PublicKeyLookup {
        val document =
            when (fetched) {
                is DocumentFetch.Found -> fetched.document

                // 相手が「もう無い」と答えた場合だけ、消えたものとして返す。
                // 落ちているだけの相手を消えた扱いにすると、後から生き返る
                DocumentFetch.Gone -> return PublicKeyLookup.Gone

                DocumentFetch.Unavailable -> return PublicKeyLookup.Unavailable
            }

        val publicKey = document.publicKey ?: return PublicKeyLookup.Unavailable

        val keyOwnerActorId = publicKey.owner ?: document.id ?: return PublicKeyLookup.Unavailable
        if (!isSameHost(keyOwnerActorId, requestUrl)) return PublicKeyLookup.Unavailable

        val decodedPublicKey =
            runCatching { RsaKeys.decodePublicKeyPem(publicKey.publicKeyPem) }
                .getOrNull() ?: return PublicKeyLookup.Unavailable

        return PublicKeyLookup.Found(
            SignatureKey(keyId = keyId, owner = keyOwnerActorId, publicKey = decodedPublicKey),
        )
    }

    /**
     * 覚えているものを使わない。
     *
     * ここで読む inbox は `Accept` の宛先と以後の配信先として記録に残る。
     * 相手が引っ越した後の古い値を書くと、こちらから届かなくなったことに
     * 気付く手がかりが無い。鍵と違って、間違いが後から直る経路が無い。
     *
     * 実際には署名の検証が直前に同じ文書を取っているので、取りに行く回数は増えない。
     * その場合に使うのはそのとき読んだ文書で、古くても取り直しの間隔のぶんだけ
     */
    override suspend fun findActor(actorId: String): RemoteActor? {
        val url = parseHttpsUrl(actorId) ?: return null
        val document = (fetch(rawUrl = actorId, requestUrl = url, useCache = false) as? DocumentFetch.Found)
            ?.document
            ?: return null

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
        )
    }

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
        useCache: Boolean,
    ): DocumentFetch {
        // `keyId` はアクター id にフラグメントを付けたもので、フラグメントはサーバーに
        // 送られない。落としてから引くと、署名の検証で取った文書を
        // `Accept` の宛先を決めるときにも使える
        val cacheKey = rawUrl.substringBefore('#')

        if (useCache) {
            documents.get(cacheKey)?.let { return DocumentFetch.Found(it) }
            // 取りに行くことにしたので、次に取り直すまでの間隔をここから数える
            recentlyFetched.put(key = cacheKey, value = Unit, ttlMillis = REFRESH_INTERVAL_MILLIS)
        } else if (!recentlyFetched.tryPut(key = cacheKey, value = Unit, ttlMillis = REFRESH_INTERVAL_MILLIS)) {
            // 取りに行く枠を取れなかった。覚えているものを使わない呼び出しは、
            // 通らない署名を投げ込むだけで誰でも起こせるので間隔を空ける。
            // 枠は取れたかどうかに関わらず塞ぐ。落ちている相手に繋ぎ直しても同じ結果になる。
            //
            // 直前に取りに行ったばかりなら、覚えているものはそのとき読んだ文書で、
            // 取り直しても同じものにしかならない
            return documents.get(cacheKey)?.let { DocumentFetch.Found(it) } ?: DocumentFetch.Unavailable
        }

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
        if (response.status == HttpStatusCode.Gone) {
            // 消えた相手の文書を覚えたままにしない。取り直しの間隔の中に来た次の
            // 呼び出しが覚えているものを使い、消えたアクターを生きているものとして扱う
            documents.invalidate(cacheKey)
            return DocumentFetch.Gone
        }
        if (!response.status.isSuccess()) return DocumentFetch.Unavailable

        val body = runCatching { response.bodyAsText() }.getOrNull() ?: return DocumentFetch.Unavailable
        if (body.length > MAX_BODY_CHARS) return DocumentFetch.Unavailable

        val document =
            runCatching { AppJson.decodeFromString(RemoteActorDocument.serializer(), body) }
                .getOrNull() ?: return DocumentFetch.Unavailable

        // 使えると分かった文書だけを覚える。中身を見ずに入れ替えると、相手が 200 で
        // 空の JSON や他所のアクターの鍵を返した瞬間に、それまでの正しい文書を失う。
        // 取り直しは通らない署名を投げ込むだけで起こせるので、外から狙える
        if (!document.hasUsableKey(requestUrl)) {
            return documents.get(cacheKey)?.let { DocumentFetch.Found(it) } ?: DocumentFetch.Found(document)
        }

        documents.put(key = cacheKey, value = document, ttlMillis = CACHE_TTL_MILLIS)
        return DocumentFetch.Found(document)
    }

    /**
     * 覚える値打ちがあるか。検証に使える鍵が、取得先と同じホストの持ち主で入っていること。
     *
     * [publicKeyOf] と同じ条件で見る。読めない鍵の文書を覚えると、そこから先の
     * 検証は鍵が無いものとして落ち、[refresh] にも進まないまま期限まで拒み続ける。
     *
     * ここを通らない文書でも、呼び出し側は結果として受け取る。そこで落ちるのは
     * 同じ判断で、覚えるかどうかだけをここで決める
     */
    private fun RemoteActorDocument.hasUsableKey(requestUrl: Url): Boolean {
        val publicKey = publicKey ?: return false

        val keyOwnerActorId = publicKey.owner ?: id ?: return false
        if (!isSameHost(keyOwnerActorId, requestUrl)) return false

        return runCatching { RsaKeys.decodePublicKeyPem(publicKey.publicKeyPem) }.isSuccess
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

    private fun isSameHost(
        raw: String,
        expected: Url,
    ): Boolean {
        val host = runCatching { Url(raw) }.getOrNull()?.host ?: return false
        return host.equals(expected.host, ignoreCase = true)
    }

    private companion object {
        /**
         * 読み込む応答の上限。アクター文書は鍵を含めても数 KB にしかならない。
         * 相手のサーバーが延々と送り続けてくる場合は、これと下のタイムアウトで止める。
         */
        const val MAX_BODY_CHARS = 64 * 1024

        /**
         * キャッシュの有効期間。
         *
         * 期限を短くしても鍵の入れ替わりには追い付けない。替わったことは通らない
         * 署名が届いて初めて分かるもので、それは [refresh] が受け持つ。ここで決まるのは
         * 相手のサーバーを引きに行く頻度だけなので、長く持つ。
         * Mastodon が相手のアカウントを古いと見なす間隔（`STALE_THRESHOLD`）に合わせて 1 日
         */
        const val CACHE_TTL_MILLIS = 24 * 60 * 60 * 1000L

        /**
         * 同じアクターを取り直すまでに空ける間隔。
         *
         * 相手が鍵を替えてから、こちらが受け取れるようになるまでの遅れの上限になる。
         * 相手のサーバーは送り直してくるので、短くする意味はこの遅れを縮めることだけ。
         * Mastodon が取得の失敗後に空ける間隔（`STOPLIGHT_COOL_OFF_TIME`）に合わせて 5 分
         */
        const val REFRESH_INTERVAL_MILLIS = 5 * 60 * 1000L

        /**
         * 覚えておくアクターの数の上限。
         *
         * キーは相手が名乗る `keyId` で、使い捨てのものをいくらでも送り込める。
         * 期限だけに任せると、送られた分がその期限のあいだ残る。溢れた分を捨てても
         * 次に要るときに取り直すだけで、判断は変わらない
         */
        const val MAX_CACHED_ACTORS = 10_000

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
