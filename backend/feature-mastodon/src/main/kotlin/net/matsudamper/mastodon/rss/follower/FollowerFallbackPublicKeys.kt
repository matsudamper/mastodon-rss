package net.matsudamper.mastodon.rss.follower

import net.matsudamper.mastodon.rss.crypto.RsaKeys
import net.matsudamper.mastodon.rss.httpsignature.PublicKeys
import net.matsudamper.mastodon.rss.httpsignature.SignatureKey

/**
 * 相手のサーバーから引けなかった公開鍵を、フォロワーの記録から引く。
 *
 * ActivityPub で鍵を配る手段はアクター文書しかないので、相手が消えると鍵は
 * どこからも取れなくなる。アカウント削除の `Delete` は本人が消えた後に届くため、
 * 取りに行くだけでは署名を検証できない。フォローを受けたときに読んだ鍵は
 * 残してあるので、相手がこちらのフォロワーだった場合はそれで検証できる。
 *
 * [remote] を先に引くのは、相手が鍵を替えていた場合に記録の方が古いため。
 * 記録した鍵を使うのは、取りに行って引けなかったときだけにする。
 */
class FollowerFallbackPublicKeys(
    private val remote: PublicKeys,
    private val followers: FollowerStore,
) : PublicKeys {
    override suspend fun find(keyId: String): SignatureKey? = remote.find(keyId) ?: findRecorded(keyId)

    private fun findRecorded(keyId: String): SignatureKey? {
        // `keyId` はアクター id にフラグメントを付けたもの。記録はアクター id で引く
        val actorUri = keyId.substringBefore('#')

        val publicKey =
            followers
                .findPublicKeyPem(actorUri)
                ?.let { runCatching { RsaKeys.decodePublicKeyPem(it) }.getOrNull() }
                ?: return null

        // 持ち主はこちらの記録で決める。相手の文書を取れていない以上、
        // keyId の名乗りを裏付けるものは記録した行しか無い
        return SignatureKey(keyId = keyId, owner = actorUri, publicKey = publicKey)
    }
}
