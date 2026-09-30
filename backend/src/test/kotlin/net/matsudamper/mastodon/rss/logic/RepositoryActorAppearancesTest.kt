package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import net.matsudamper.activitypub.actor.ActorAppearance
import net.matsudamper.activitypub.actor.ActorLink
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.repository.FeedHeader
import net.matsudamper.mastodon.rss.repository.NewFeed

class RepositoryActorAppearancesTest {
    private val repositories = FakeRepositories()

    private val appearances = RepositoryActorAppearances(
        accounts = repositories.accounts,
        feeds = repositories.feeds,
        headers = repositories.feedHeaders,
    )

    @Test
    fun `フィードを持つアカウントはサイトとフィードのリンクとアイコンとヘッダーの版を返す`() {
        val account = assertNotNull(repositories.accounts.add(username = USERNAME, createdAt = NOW))
        val feed = assertNotNull(
            repositories.feeds.add(
                NewFeed(
                    accountId = account.id,
                    url = FEED_URL,
                    title = null,
                    siteUrl = SITE_URL,
                    format = null,
                    iconUrl = ICON_URL,
                    pollIntervalSeconds = 900,
                ),
            ),
        )
        repositories.feedHeaders.save(
            feedId = feed.id,
            header = FeedHeader(
                sourceUrl = "https://example.com/header.png",
                contentType = "image/png",
                revision = HEADER_REVISION,
                path = "header.png",
                fetchedAt = NOW,
                expiresAt = NOW,
            ),
        )

        assertEquals(
            ActorAppearance(
                links = listOf(
                    ActorLink(name = "サイト", url = SITE_URL),
                    ActorLink(name = "フィード", url = FEED_URL),
                ),
                iconVersion = ActorIconVersion.of(ICON_URL),
                headerVersion = HEADER_REVISION,
            ),
            appearances.find(USERNAME),
        )
    }

    @Test
    fun `フィードを持たないアカウントは何も返さない`() {
        repositories.accounts.add(username = USERNAME, createdAt = NOW)

        assertEquals(ActorAppearance.EMPTY, appearances.find(USERNAME))
    }

    private companion object {
        const val USERNAME = "feed1"
        const val FEED_URL = "https://example.com/feed.xml"
        const val SITE_URL = "https://example.com/"
        const val ICON_URL = "https://example.com/icon.png"
        const val HEADER_REVISION = "0123456789abcdef"

        val NOW: Instant = Instant.parse("2026-09-01T00:00:00Z")
    }
}
