package net.matsudamper.mastodon.rss.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import net.matsudamper.mastodon.rss.repository.entity.FeedItemId
import net.matsudamper.mastodon.rss.shared.PublicNoteId

class FeedItemNoteLinkTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-feed-item-note-link-test")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `Noteの保存と記事への紐付けをまとめて行う`() {
        withRepositories { repositories ->
            val feed = repositories.addFeed()
            val item = assertNotNull(
                repositories.feedItems.add(
                    NewFeedItem(
                        feedId = feed.id,
                        itemKey = "item-1",
                        title = "題名",
                        link = "https://example.com/1",
                        contentHtml = "<p>本文</p>",
                        publishedAt = CREATED_AT,
                        importedAt = CREATED_AT,
                        state = FeedItemState.PENDING,
                    ),
                ),
            )
            val note = note(PublicNoteId("note-1"))

            val linked = repositories.feedItems.linkNote(feedId = item.id, note = note)

            assertEquals(note.publicId, linked)
            assertNotNull(repositories.notes.find(note.publicId))
            assertEquals(note.publicId, assertNotNull(repositories.feedItems.find(item.id)).noteId)
        }
    }

    @Test
    fun `既に投稿が紐付いていれば新しいNoteを残さない`() {
        withRepositories { repositories ->
            val feed = repositories.addFeed()
            val item = assertNotNull(
                repositories.feedItems.add(
                    NewFeedItem(
                        feedId = feed.id,
                        itemKey = "item-1",
                        title = "題名",
                        link = "https://example.com/1",
                        contentHtml = "<p>本文</p>",
                        publishedAt = CREATED_AT,
                        importedAt = CREATED_AT,
                        state = FeedItemState.PENDING,
                    ),
                ),
            )
            val first = note(PublicNoteId("note-1"))
            val second = note(PublicNoteId("note-2"))
            repositories.feedItems.linkNote(feedId = item.id, note = first)

            val linked = repositories.feedItems.linkNote(feedId = item.id, note = second)

            assertEquals(first.publicId, linked)
            assertNotNull(repositories.notes.find(first.publicId))
            assertNull(repositories.notes.find(second.publicId))
            assertEquals(first.publicId, assertNotNull(repositories.feedItems.find(item.id)).noteId)
        }
    }

    @Test
    fun `記事への紐付けに失敗したらNoteも残さない`() {
        withRepositories { repositories ->
            val note = note(PublicNoteId("note-1"))

            assertFailsWith<IllegalStateException> {
                repositories.feedItems.linkNote(feedId = FeedItemId(404), note = note)
            }

            assertNull(repositories.notes.find(note.publicId))
        }
    }

    private fun Repositories.addFeed(): Feed {
        val account = assertNotNull(accounts.add(username = "feed1", createdAt = CREATED_AT))
        return assertNotNull(
            feeds.add(
                NewFeed(
                    accountId = account.id,
                    url = "https://example.com/feed.xml",
                    title = null,
                    siteUrl = null,
                    format = null,
                    iconUrl = null,
                    pollIntervalSeconds = 900,
                ),
            ),
        )
    }

    private fun note(publicId: PublicNoteId): NewNote = NewNote(
        username = "feed1",
        publicId = publicId,
        contentHtml = "<p>本文</p>",
        publishedAt = CREATED_AT,
    )

    private fun withRepositories(block: (Repositories) -> Unit) {
        val dbPath = tempDir.resolve("test.db")
        TestSchema.applyTo(dbPath)
        createRepositories(DatabaseConfig(path = dbPath)).use(block)
    }

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-08-16T01:02:03.123456Z")
    }
}
