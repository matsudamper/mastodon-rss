package net.matsudamper.mastodon.rss

import java.security.KeyPair
import net.matsudamper.mastodon.rss.crypto.RsaKeys

/**
 * 署名付きのリクエストを送ってくる相手の想定。
 *
 * 2048bit の鍵生成はテストごとに走らせると無視できない時間になるので共有する。
 */
object TestRemoteActor {
    const val ACTOR_ID: String = "https://remote.example/users/alice"
    const val KEY_ID: String = "$ACTOR_ID#main-key"
    const val INBOX: String = "$ACTOR_ID/inbox"

    val keyPair: KeyPair by lazy { RsaKeys.generateKeyPair() }

    /** この相手だけを引ける [RemoteActors][net.matsudamper.mastodon.rss.actor.RemoteActors] */
    fun remoteActors(inbox: String? = INBOX): TestRemoteActors =
        TestRemoteActors.of(
            keyId = KEY_ID,
            owner = ACTOR_ID,
            publicKey = keyPair.public,
            inbox = inbox,
        )
}
