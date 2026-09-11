package net.matsudamper.mastodon.rss.logic

import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.TestActorKey
import net.matsudamper.mastodon.rss.TestDelivery
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.TestWebPageUrls
import net.matsudamper.mastodon.rss.actor.ActorPublisher

class AccountServiceProfileTest {
    private val iconStore = FeedIconStore(Files.createTempDirectory("account-profile-icon"))

    @Test
    fun `30文字を超える表示名をそのまま保存できる`() = runTest {
        val repositories = FakeRepositories()
        assertNotNull(repositories.accounts.add(username = USERNAME, createdAt = CREATED_AT))
        val displayName = "長".repeat(1_000)

        val result = serviceOf(repositories).updateProfile(
            username = USERNAME,
            displayName = displayName,
            summary = "",
        )

        assertIs<AccountService.UpdateProfileResult.Success>(result)
        assertEquals(displayName, assertNotNull(repositories.accounts.findByUsername(USERNAME)).displayName)
    }

    private fun serviceOf(repositories: FakeRepositories): AccountService = AccountService(
        accounts = repositories.accounts,
        followers = repositories.followers,
        actorPublisher = ActorPublisher(
            notes = RepositoryNoteStore(repositories.notes),
            followers = RepositoryFollowerStore(repositories.followers),
            delivery = TestDelivery(),
            actorKey = TestActorKey.value,
            feedLinks = RepositoryFeedLinks(
                accounts = repositories.accounts,
                feeds = repositories.feeds,
                headers = repositories.feedHeaders,
            ),
            profiles = RepositoryActorProfiles(repositories.accounts),
            webPages = TestWebPageUrls,
        ),
        iconFiles = AccountIconFiles(
            feeds = repositories.feeds,
            icons = repositories.feedIcons,
            store = iconStore,
        ),
        domain = TestLocalActor.DOMAIN,
    )

    private companion object {
        const val USERNAME = "feed1"
        val CREATED_AT: Instant = Instant.parse("2026-08-16T01:02:03Z")
    }
}
