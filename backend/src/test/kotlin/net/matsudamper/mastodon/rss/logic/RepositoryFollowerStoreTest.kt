package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.matsudamper.kotpub.actor.RemoteActor
import net.matsudamper.kotpub.actor.RemoteActorProfile
import net.matsudamper.mastodon.rss.FakeFollowerRepository

class RepositoryFollowerStoreTest {
    private val followers = FakeFollowerRepository()

    private val store = RepositoryFollowerStore(followers = followers, maxDeliveryTargetsPerHost = { LIMIT })

    @Test
    fun `同じホストから inbox の異なるフォローが上限に達すると新しいフォローを断る`() {
        recordSpamFollowersUpToLimit(USERNAME)

        assertTrue(store.refuses(USERNAME, spamActor("new")))
    }

    @Test
    fun `sharedInbox にまとまる相手は何人いても断らない`() {
        repeat(LIMIT * 2) { index ->
            record(USERNAME, socialActor("$index"))
        }

        assertFalse(store.refuses(USERNAME, socialActor("new")))
    }

    @Test
    fun `上限に達していても記録済みの相手が同じ inbox で送り直したら断らない`() {
        recordSpamFollowersUpToLimit(USERNAME)

        assertFalse(store.refuses(USERNAME, spamActor("0")))
    }

    @Test
    fun `上限に達していても自分だけの inbox を移す送り直しは断らない`() {
        recordSpamFollowersUpToLimit(USERNAME)

        assertFalse(store.refuses(USERNAME, spamActor("0").copy(inbox = "https://spam.example/users/0/moved-inbox")))
    }

    @Test
    fun `sharedInbox で記録した相手が inbox を分けて送り直すと上限で断る`() {
        // 最後の 1 人が sharedInbox に残るので、sharedInbox の 1 か所は数え続ける
        repeat(LIMIT + 1) { index ->
            record(USERNAME, spamActor("$index").copy(sharedInbox = "https://spam.example/inbox"))
        }
        // sharedInbox の 1 か所と合わせて上限に達するまでは受け付ける
        repeat(LIMIT - 1) { index ->
            assertFalse(store.refuses(USERNAME, spamActor("$index")))
            record(USERNAME, spamActor("$index"))
        }

        assertTrue(store.refuses(USERNAME, spamActor("${LIMIT - 1}")))
    }

    @Test
    fun `別のアカウントへのフォローも合わせて数える`() {
        recordSpamFollowersUpToLimit(OTHER_USERNAME)

        assertTrue(store.refuses(USERNAME, spamActor("new")))
    }

    @Test
    fun `別のホストのフォローは上限に数えない`() {
        recordSpamFollowersUpToLimit(USERNAME)

        assertFalse(store.refuses(USERNAME, spamActor("1").copy(actorId = "https://other.example/users/1")))
    }

    @Test
    fun `上限は判定のたびに読み直す`() {
        var limit = LIMIT
        val store = RepositoryFollowerStore(followers = followers, maxDeliveryTargetsPerHost = { limit })
        recordSpamFollowersUpToLimit(USERNAME)

        limit = LIMIT + 1

        assertFalse(store.refuses(USERNAME, spamActor("new")))
    }

    private fun recordSpamFollowersUpToLimit(username: String) {
        repeat(LIMIT) { index ->
            record(username, spamActor("$index"))
        }
    }

    private fun record(
        username: String,
        actor: RemoteActor,
    ) {
        store.record(
            username = username,
            follower = actor,
            followActivityUri = "${actor.actorId}#follow-$username",
            receivedAt = NOW,
            acceptBody = "{}",
        )
    }

    private fun spamActor(name: String): RemoteActor = actor(
        actorId = "https://spam.example/users/$name",
        inbox = "https://spam.example/users/$name/inbox",
        sharedInbox = null,
    )

    private fun socialActor(name: String): RemoteActor = actor(
        actorId = "https://social.example/users/$name",
        inbox = "https://social.example/users/$name/inbox",
        sharedInbox = "https://social.example/inbox",
    )

    private fun actor(
        actorId: String,
        inbox: String,
        sharedInbox: String?,
    ): RemoteActor = RemoteActor(
        actorId = actorId,
        inbox = inbox,
        sharedInbox = sharedInbox,
        publicKeyPem = "pem",
        profile = RemoteActorProfile(
            preferredUsername = null,
            displayName = null,
            profileUrl = null,
            iconUrl = null,
            headerUrl = null,
            emojis = listOf(),
        ),
    )

    private companion object {
        const val USERNAME = "feed1"

        const val OTHER_USERNAME = "feed2"

        const val LIMIT = 3

        val NOW: Instant = Instant.parse("2026-09-01T00:00:00Z")
    }
}
