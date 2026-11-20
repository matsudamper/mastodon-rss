package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.matsudamper.mastodon.rss.FakeFollowerRepository
import net.matsudamper.mastodon.rss.repository.IncomingFollow
import net.matsudamper.mastodon.rss.repository.NewRemoteActor
import net.matsudamper.mastodon.rss.repository.RemoteActorProfile

class RepositoryFollowerStoreTest {
    private val followers = FakeFollowerRepository()

    private val store = RepositoryFollowerStore(followers)

    @Test
    fun `同じホストから inbox の異なるフォローが上限に達すると新しいフォローを断る`() {
        repeat(RepositoryFollowerStore.MAX_DELIVERY_TARGETS_PER_ACTOR_HOST) { index ->
            record(actorUri = "https://spam.example/users/$index", inbox = "https://spam.example/users/$index/inbox", sharedInbox = null)
        }

        assertTrue(store.refuses(USERNAME, "https://spam.example/users/new"))
    }

    @Test
    fun `sharedInbox にまとまる相手は何人いても断らない`() {
        repeat(RepositoryFollowerStore.MAX_DELIVERY_TARGETS_PER_ACTOR_HOST * 2) { index ->
            record(
                actorUri = "https://social.example/users/$index",
                inbox = "https://social.example/users/$index/inbox",
                sharedInbox = "https://social.example/inbox",
            )
        }

        assertFalse(store.refuses(USERNAME, "https://social.example/users/new"))
    }

    @Test
    fun `上限に達していても記録済みの相手からの送り直しは断らない`() {
        repeat(RepositoryFollowerStore.MAX_DELIVERY_TARGETS_PER_ACTOR_HOST) { index ->
            record(actorUri = "https://spam.example/users/$index", inbox = "https://spam.example/users/$index/inbox", sharedInbox = null)
        }

        assertFalse(store.refuses(USERNAME, "https://spam.example/users/0"))
    }

    @Test
    fun `別のホストのフォローは上限に数えない`() {
        repeat(RepositoryFollowerStore.MAX_DELIVERY_TARGETS_PER_ACTOR_HOST) { index ->
            record(actorUri = "https://spam.example/users/$index", inbox = "https://spam.example/users/$index/inbox", sharedInbox = null)
        }

        assertFalse(store.refuses(USERNAME, "https://other.example/users/1"))
    }

    private fun record(
        actorUri: String,
        inbox: String,
        sharedInbox: String?,
    ) {
        followers.record(
            IncomingFollow(
                username = USERNAME,
                follower = NewRemoteActor(
                    actorUri = actorUri,
                    inbox = inbox,
                    sharedInbox = sharedInbox,
                    publicKeyPem = "pem",
                    profile = RemoteActorProfile(preferredUsername = null, displayName = null, profileUrl = null, iconUrl = null),
                ),
                followActivityUri = "$actorUri#follow",
                receivedAt = NOW,
                acceptBody = "{}",
            ),
        )
    }

    private companion object {
        const val USERNAME = "feed1"

        val NOW: Instant = Instant.parse("2026-09-01T00:00:00Z")
    }
}
