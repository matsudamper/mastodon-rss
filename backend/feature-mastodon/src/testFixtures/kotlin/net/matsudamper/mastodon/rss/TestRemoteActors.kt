package net.matsudamper.mastodon.rss

import java.security.PublicKey
import net.matsudamper.mastodon.rss.actor.RemoteActor
import net.matsudamper.mastodon.rss.actor.RemoteActors
import net.matsudamper.mastodon.rss.crypto.RsaKeys
import net.matsudamper.mastodon.rss.httpsignature.SignatureKey

/**
 * 相手のアクターの引き先の差し替え。
 *
 * 本番は相手のサーバーに GET しに行くので、テストからは必ずこちらを通す。
 * 何も渡さなければ「どの keyId も引けず、どの inbox も分からない」サーバーになる。
 */
class TestRemoteActors(
    private val keys: Map<String, SignatureKey> = emptyMap(),
    private val actors: Map<String, RemoteActor> = emptyMap(),
) : RemoteActors {
    var findCallCount: Int = 0
        private set

    override suspend fun find(keyId: String): SignatureKey? {
        findCallCount++
        return keys[keyId]
    }

    override suspend fun findActor(actorId: String): RemoteActor? = actors[actorId]

    override fun close() {
    }

    companion object {
        fun of(
            keyId: String,
            owner: String,
            publicKey: PublicKey,
            inbox: String? = null,
        ): TestRemoteActors =
            TestRemoteActors(
                keys = mapOf(keyId to SignatureKey(keyId = keyId, owner = owner, publicKey = publicKey)),
                actors = if (inbox == null) {
                    emptyMap()
                } else {
                    mapOf(
                        owner to RemoteActor(
                            actorId = owner,
                            inbox = inbox,
                            sharedInbox = null,
                            publicKeyPem = RsaKeys.encodeToPem(publicKey),
                        ),
                    )
                },
            )
    }
}
