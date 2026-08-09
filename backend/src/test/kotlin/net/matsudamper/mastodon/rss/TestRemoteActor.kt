package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.crypto.RsaKeys
import java.security.KeyPair

/**
 * 署名付きのリクエストを送ってくる相手の想定。
 *
 * 2048bit の鍵生成はテストごとに走らせると無視できない時間になるので共有する。
 */
object TestRemoteActor {
    const val ACTOR_ID: String = "https://remote.example/users/alice"
    const val KEY_ID: String = "$ACTOR_ID#main-key"

    val keyPair: KeyPair by lazy { RsaKeys.generateKeyPair() }

    /** この相手の鍵だけを引ける [PublicKeys][net.matsudamper.mastodon.rss.httpsignature.PublicKeys] */
    fun publicKeys(): TestPublicKeys =
        TestPublicKeys.of(
            keyId = KEY_ID,
            owner = ACTOR_ID,
            publicKey = keyPair.public,
        )
}
