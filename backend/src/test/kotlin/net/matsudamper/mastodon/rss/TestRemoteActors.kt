package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.actor.RemoteActors
import net.matsudamper.mastodon.rss.httpsignature.SignatureKey
import java.security.PublicKey

/**
 * 相手のアクターの引き先の差し替え。
 *
 * 本番は相手のサーバーに GET しに行くので、テストからは必ずこちらを通す。
 * 何も渡さなければ「どの keyId も引けず、どの inbox も分からない」サーバーになる。
 */
class TestRemoteActors(
    private val keys: Map<String, SignatureKey> = emptyMap(),
    private val inboxes: Map<String, String> = emptyMap(),
) : RemoteActors {
    var findCallCount: Int = 0
        private set

    override suspend fun find(keyId: String): SignatureKey? {
        findCallCount++
        return keys[keyId]
    }

    override suspend fun findInbox(actorId: String): String? = inboxes[actorId]

    companion object {
        fun of(
            keyId: String,
            owner: String,
            publicKey: PublicKey,
            inbox: String? = null,
        ): TestRemoteActors =
            TestRemoteActors(
                keys = mapOf(keyId to SignatureKey(keyId = keyId, owner = owner, publicKey = publicKey)),
                inboxes = if (inbox == null) emptyMap() else mapOf(owner to inbox),
            )
    }
}
