package net.matsudamper.mastodon.rss.follower

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import net.matsudamper.mastodon.rss.FakeFollowerStore
import net.matsudamper.mastodon.rss.TestRemoteActor
import net.matsudamper.mastodon.rss.TestRemoteActors
import net.matsudamper.mastodon.rss.actor.RemoteActor
import net.matsudamper.mastodon.rss.crypto.RsaKeys

// 消えたアクターの鍵をフォローの記録から引けること。
// 相手のサーバーに GET しに行く方は TestRemoteActors で差し替える。
class FollowerFallbackPublicKeysTest {
    private val now: Instant = Instant.parse("2026-08-10T00:00:00Z")

    private fun followers(publicKeyPem: String = RsaKeys.encodeToPem(TestRemoteActor.keyPair.public)): FakeFollowerStore =
        FakeFollowerStore().apply {
            record(
                username = "admin",
                follower = RemoteActor(
                    actorId = TestRemoteActor.ACTOR_ID,
                    inbox = TestRemoteActor.INBOX,
                    sharedInbox = null,
                    publicKeyPem = publicKeyPem,
                ),
                followActivityUri = "https://remote.example/activities/1",
                receivedAt = now,
            )
        }

    @Test
    fun `相手のサーバーから引けたらそちらを使う`() =
        runBlocking {
            val remote = TestRemoteActor.remoteActors()

            val key =
                FollowerFallbackPublicKeys(remote = remote, followers = followers())
                    .find(TestRemoteActor.KEY_ID)

            assertEquals(1, remote.findCallCount)
            assertEquals(TestRemoteActor.ACTOR_ID, key?.owner)
            assertEquals(TestRemoteActor.keyPair.public, key?.publicKey)
        }

    @Test
    fun `引けなければフォローの記録から引く`() =
        runBlocking {
            val key =
                FollowerFallbackPublicKeys(remote = TestRemoteActors(), followers = followers())
                    .find(TestRemoteActor.KEY_ID)

            // 削除された相手の Delete を検証できるのはこの経路だけ
            assertEquals(TestRemoteActor.keyPair.public, key?.publicKey)
            // 持ち主は keyId の名乗りではなく、記録したアクター id
            assertEquals(TestRemoteActor.ACTOR_ID, key?.owner)
            assertEquals(TestRemoteActor.KEY_ID, key?.keyId)
        }

    @Test
    fun `フォローの記録が無ければ引けない`() =
        runBlocking {
            val key =
                FollowerFallbackPublicKeys(remote = TestRemoteActors(), followers = FakeFollowerStore())
                    .find(TestRemoteActor.KEY_ID)

            assertNull(key)
        }

    @Test
    fun `記録した PEM を読めなければ引けない`() =
        runBlocking {
            val key =
                FollowerFallbackPublicKeys(
                    remote = TestRemoteActors(),
                    followers = followers(publicKeyPem = "鍵ではない"),
                ).find(TestRemoteActor.KEY_ID)

            assertNull(key)
        }
}
