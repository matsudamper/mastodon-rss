package net.matsudamper.mastodon.rss.actor

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
import kotlinx.serialization.Serializable
import net.matsudamper.mastodon.rss.activitypub.ActivityPubContentTypes
import net.matsudamper.mastodon.rss.crypto.RsaKeys
import net.matsudamper.mastodon.rss.httpsignature.SignatureKey
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.repository.ExpiringCache
import net.matsudamper.mastodon.rss.repository.createExpiringCache
import java.io.Closeable

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
    private val client: HttpClient = defaultClient(),
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

        // owner が無い文書もあるので、その場合はアクター自身の id を持ち主とみなす
        val owner = publicKey.owner ?: document.id ?: return null
        if (!isSameHost(owner, url)) return null

        val parsed =
            runCatching { RsaKeys.decodePublicKeyPem(publicKey.publicKeyPem) }
                .getOrNull() ?: return null

        return SignatureKey(keyId = keyId, owner = owner, publicKey = parsed)
    }

    override suspend fun findInbox(actorId: String): String? {
        val url = parseHttpsUrl(actorId) ?: return null
        val document = fetch(actorId, url) ?: return null

        val inbox = document.inbox ?: return null

        // 宛先はこちらが POST しに行く先になる。アクターと同じホストに限ることで、
        // 相手が自分の文書に書いた URL でこちらから他所へ POST させる形を塞ぐ
        if (parseHttpsUrl(inbox) == null) return null
        if (!isSameHost(inbox, url)) return null

        return inbox
    }

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

        fun defaultClient(): HttpClient =
            HttpClient(CIO) {
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
    val id: String? = null,
    val inbox: String? = null,
    val publicKey: RemoteActorPublicKey? = null,
)

@Serializable
private data class RemoteActorPublicKey(
    val id: String? = null,
    val owner: String? = null,
    val publicKeyPem: String,
)
