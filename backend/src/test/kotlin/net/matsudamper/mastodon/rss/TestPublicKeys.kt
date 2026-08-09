package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.httpsignature.PublicKeys
import net.matsudamper.mastodon.rss.httpsignature.SignatureKey
import java.security.PublicKey

/**
 * 署名検証に使う公開鍵の差し替え。
 *
 * 本番は相手のサーバーに GET しに行くので、テストからは必ずこちらを通す。
 * 何も渡さなければ「どの keyId も引けない」サーバーになる。
 */
class TestPublicKeys(
    private val keys: Map<String, SignatureKey> = emptyMap(),
) : PublicKeys {
    var findCallCount: Int = 0
        private set

    override suspend fun find(keyId: String): SignatureKey? {
        findCallCount++
        return keys[keyId]
    }

    companion object {
        fun of(
            keyId: String,
            owner: String,
            publicKey: PublicKey,
        ): TestPublicKeys =
            TestPublicKeys(
                mapOf(keyId to SignatureKey(keyId = keyId, owner = owner, publicKey = publicKey)),
            )
    }
}
