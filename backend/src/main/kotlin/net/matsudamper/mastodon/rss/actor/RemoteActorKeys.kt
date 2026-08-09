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
import net.matsudamper.mastodon.rss.httpsignature.PublicKeys
import net.matsudamper.mastodon.rss.httpsignature.SignatureKey
import net.matsudamper.mastodon.rss.json.AppJson
import java.io.Closeable

/**
 * `keyId` の指す先を実際に GET して公開鍵を取る。
 *
 * ActivityPub では鍵の配布方法が「アクター文書の中に入っている」しかないので、
 * 知らない相手から署名付きのリクエストが来たら、その場で相手のサーバーに
 * 取りに行くことになる。
 *
 * 取得先は相手が `keyId` として指定してきた URL で、こちらが選べない。
 * つまり任意の URL に GET させられる口でもあるため、次を守る。
 *
 * - https のみ。平文で取った鍵は途中で差し替えられる
 * - リダイレクトで別ホストに移ったら捨てる。移った先のサーバーが
 *   他人のアクターの鍵を名乗れてしまう
 * - 鍵の持ち主（`owner`）は `keyId` と同じホストであること。
 *   他所のホストのアクターの鍵だと言い張るものを信じない
 * - 大きすぎる応答は読まない
 *
 * 取得結果のキャッシュはまだ持たない。フォローのたびに 1 回引きに行く。
 * 毎回引かない仕組みは TODO.md の Phase 2 に項目がある。
 */
class RemoteActorKeys(
    private val client: HttpClient = defaultClient(),
) : PublicKeys,
    Closeable {
    override suspend fun find(keyId: String): SignatureKey? {
        val url = runCatching { Url(keyId) }.getOrNull() ?: return null
        if (url.protocol != URLProtocol.HTTPS) return null

        val response =
            runCatching {
                client.get(keyId) {
                    header(HttpHeaders.Accept, ActivityPubContentTypes.ActivityJson.toString())
                }
            }.getOrNull() ?: return null

        if (!response.status.isSuccess()) return null

        // リダイレクトを追った結果、別のホストに移っていたら信用しない
        val fetchedFrom = response.request.url.host
        if (!fetchedFrom.equals(url.host, ignoreCase = true)) return null

        val body = runCatching { response.bodyAsText() }.getOrNull() ?: return null
        if (body.length > MAX_BODY_CHARS) return null

        val document =
            runCatching { AppJson.decodeFromString(RemoteActorDocument.serializer(), body) }
                .getOrNull() ?: return null

        val publicKey = document.publicKey ?: return null

        // owner が無い文書もあるので、その場合はアクター自身の id を持ち主とみなす
        val owner = publicKey.owner ?: document.id ?: return null
        val ownerHost = runCatching { Url(owner) }.getOrNull()?.host ?: return null
        if (!ownerHost.equals(url.host, ignoreCase = true)) return null

        val parsed =
            runCatching { RsaKeys.decodePublicKeyPem(publicKey.publicKeyPem) }
                .getOrNull() ?: return null

        return SignatureKey(keyId = keyId, owner = owner, publicKey = parsed)
    }

    override fun close() {
        client.close()
    }

    private companion object {
        /**
         * 読み込む応答の上限。アクター文書は鍵を含めても数 KB にしかならない。
         * 相手のサーバーが延々と送り続けてくる場合は、これと下のタイムアウトで止める。
         */
        const val MAX_BODY_CHARS = 64 * 1024

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
 * 相手のアクター文書のうち、鍵の取得に必要な部分だけ。
 *
 * こちらが返す [net.matsudamper.mastodon.rss.activitypub.Actor] を使い回さないのは、
 * あちらが「返すときに必ず入れるもの」を必須にしているため。相手の実装が
 * `following` を省略しただけで鍵が読めなくなるのは筋が悪い。
 */
@Serializable
private data class RemoteActorDocument(
    val id: String? = null,
    val publicKey: RemoteActorPublicKey? = null,
)

@Serializable
private data class RemoteActorPublicKey(
    val id: String? = null,
    val owner: String? = null,
    val publicKeyPem: String,
)
