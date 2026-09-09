package net.matsudamper.mastodon.rss.follower

import net.matsudamper.mastodon.rss.crypto.RsaKeys
import net.matsudamper.mastodon.rss.httpsignature.PublicKeyLookup
import net.matsudamper.mastodon.rss.httpsignature.PublicKeys
import net.matsudamper.mastodon.rss.httpsignature.SignatureKey

/**
 * 相手が消えて引けなくなった公開鍵を、フォロワーの記録から引く。
 *
 * ActivityPub で鍵を配る手段はアクター文書しかないので、相手が消えると鍵は
 * どこからも取れなくなる。アカウント削除の `Delete` は本人が消えた後に届くため、
 * 取りに行くだけでは署名を検証できない。フォローを受けたときに読んだ鍵は
 * 残してあるので、相手がこちらのフォロワーだった場合はそれで検証できる。
 *
 * 記録した鍵を使うのは、相手のサーバーが「もう無い」と答えたときだけにする。
 * 取りに行けなかっただけの場合にも使うと、相手が鍵を替えた後に一時的な障害が
 * 起きている間だけ、失効したはずの古い鍵で署名が通る。その鍵を持っている者は
 * 本人の `Delete` を装ってフォローを消させられる。
 *
 * 引けたときは記録を新しくする。`Follow` を受けたときの鍵のままにしておくと、
 * 相手が鍵を替えてから消えた場合に、記録の鍵では検証できない。
 */
class FollowerFallbackPublicKeys(
    private val remote: PublicKeys,
    private val followers: FollowerStore,
) : PublicKeys {
    override suspend fun find(keyId: String): PublicKeyLookup =
        when (val lookup = remote.find(keyId)) {
            is PublicKeyLookup.Found -> {
                followers.rememberPublicKeyPem(
                    // 引き当てるのは keyId の名乗りではなく、検証で決まった持ち主
                    actorUri = lookup.key.owner,
                    publicKeyPem = RsaKeys.encodeToPem(lookup.key.publicKey),
                )
                lookup
            }

            PublicKeyLookup.Gone -> findRecorded(keyId) ?: PublicKeyLookup.Gone

            PublicKeyLookup.Unavailable -> PublicKeyLookup.Unavailable
        }

    private fun findRecorded(keyId: String): PublicKeyLookup.Found? {
        // `keyId` はアクター id にフラグメントを付けたもの。記録はアクター id で引く
        val actorUri = keyId.substringBefore('#')

        val publicKey =
            followers
                .findPublicKeyPem(actorUri)
                ?.let { runCatching { RsaKeys.decodePublicKeyPem(it) }.getOrNull() }
                ?: return null

        // 持ち主はこちらの記録で決める。相手の文書を取れていない以上、
        // keyId の名乗りを裏付けるものは記録した行しか無い
        return PublicKeyLookup.Found(
            SignatureKey(keyId = keyId, owner = actorUri, publicKey = publicKey),
        )
    }
}
