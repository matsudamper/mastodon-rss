package net.matsudamper.mastodon.rss.follower

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import net.matsudamper.mastodon.rss.FakeFollowerStore
import net.matsudamper.mastodon.rss.TestRemoteActor
import net.matsudamper.mastodon.rss.TestRemoteActors
import net.matsudamper.mastodon.rss.actor.RemoteActor
import net.matsudamper.mastodon.rss.crypto.RsaKeys
import net.matsudamper.mastodon.rss.httpsignature.PublicKeyLookup

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

    /** アクターが消えたと答えるサーバー */
    private fun gone(): TestRemoteActors = TestRemoteActors(missing = PublicKeyLookup.Gone)

    @Test
    fun `相手のサーバーから引けたらそちらを使う`() =
        runBlocking {
            val remote = TestRemoteActor.remoteActors()

            val lookup =
                FollowerFallbackPublicKeys(remote = remote, followers = followers())
                    .find(TestRemoteActor.KEY_ID)

            assertEquals(1, remote.findCallCount)
            assertEquals(TestRemoteActor.keyPair.public, (lookup as PublicKeyLookup.Found).key.publicKey)
            assertEquals(TestRemoteActor.ACTOR_ID, lookup.key.owner)
        }

    @Test
    fun `消えた相手はフォローの記録から引く`() =
        runBlocking {
            val lookup =
                FollowerFallbackPublicKeys(remote = gone(), followers = followers())
                    .find(TestRemoteActor.KEY_ID)

            // 削除された相手の Delete を検証できるのはこの経路だけ
            val found = lookup as PublicKeyLookup.Found
            assertEquals(TestRemoteActor.keyPair.public, found.key.publicKey)
            // 持ち主は keyId の名乗りではなく、記録したアクター id
            assertEquals(TestRemoteActor.ACTOR_ID, found.key.owner)
            assertEquals(TestRemoteActor.KEY_ID, found.key.keyId)
        }

    @Test
    fun `取りに行けなかっただけなら記録の鍵は使わない`() =
        runBlocking {
            // 相手が鍵を替えた後にこの経路を通すと、失効した鍵で署名が通ってしまう
            val lookup =
                FollowerFallbackPublicKeys(remote = TestRemoteActors(), followers = followers())
                    .find(TestRemoteActor.KEY_ID)

            assertEquals(PublicKeyLookup.Unavailable, lookup)
        }

    @Test
    fun `フォローの記録が無ければ引けない`() =
        runBlocking {
            val lookup =
                FollowerFallbackPublicKeys(remote = gone(), followers = FakeFollowerStore())
                    .find(TestRemoteActor.KEY_ID)

            assertEquals(PublicKeyLookup.Gone, lookup)
        }

    @Test
    fun `記録した PEM を読めなければ引けない`() =
        runBlocking {
            val lookup =
                FollowerFallbackPublicKeys(
                    remote = gone(),
                    followers = followers(publicKeyPem = "鍵ではない"),
                ).find(TestRemoteActor.KEY_ID)

            assertEquals(PublicKeyLookup.Gone, lookup)
        }
}
