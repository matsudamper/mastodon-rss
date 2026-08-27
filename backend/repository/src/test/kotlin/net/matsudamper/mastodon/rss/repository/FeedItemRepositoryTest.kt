package net.matsudamper.mastodon.rss.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedItemRepositoryTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-feed-item-test")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `記事を追加してフィードの件数に入る`() {
        withRepositories { repositories ->
            val feed = repositories.addFeed()

            val added = assertNotNull(
                repositories.feedItems.add(
                    item(
                        feedId = feed.id,
                        itemKey = "a",
                        publishedAt = Instant.parse("2026-01-01T00:00:00Z"),
                    ),
                ),
            )

            assertEquals("a", added.itemKey)
            assertEquals(FeedItemState.SKIPPED, added.state)
            assertEquals(1, repositories.feedItems.countByFeed(feed.id))
        }
    }

    @Test
    fun `同じ鍵は二重に保存できない`() {
        withRepositories { repositories ->
            val feed = repositories.addFeed()
            repositories.feedItems.add(item(feedId = feed.id, itemKey = "same"))

            assertNull(repositories.feedItems.add(item(feedId = feed.id, itemKey = "same")))
            assertEquals(1, repositories.feedItems.countByFeed(feed.id))
        }
    }

    @Test
    fun `findExistingKeys は既にある鍵だけ返す`() {
        withRepositories { repositories ->
            val feed = repositories.addFeed()
            repositories.feedItems.add(item(feedId = feed.id, itemKey = "keep"))

            assertEquals(
                setOf("keep"),
                repositories.feedItems.findExistingKeys(feed.id, listOf("keep", "missing")),
            )
            assertEquals(emptySet(), repositories.feedItems.findExistingKeys(feed.id, emptyList()))
        }
    }

    @Test
    fun `findPending は古い順で publishedAt が無いものを後ろにする`() {
        withRepositories { repositories ->
            val feed = repositories.addFeed()
            val older = assertNotNull(
                repositories.feedItems.add(
                    item(
                        feedId = feed.id,
                        itemKey = "old",
                        publishedAt = Instant.parse("2026-01-01T00:00:00Z"),
                        state = FeedItemState.PENDING,
                    ),
                ),
            )
            val newer = assertNotNull(
                repositories.feedItems.add(
                    item(
                        feedId = feed.id,
                        itemKey = "new",
                        publishedAt = Instant.parse("2026-06-01T00:00:00Z"),
                        state = FeedItemState.PENDING,
                    ),
                ),
            )
            val undated = assertNotNull(
                repositories.feedItems.add(
                    item(
                        feedId = feed.id,
                        itemKey = "none",
                        publishedAt = null,
                        state = FeedItemState.PENDING,
                    ),
                ),
            )
            repositories.feedItems.add(
                item(
                    feedId = feed.id,
                    itemKey = "skipped",
                    state = FeedItemState.SKIPPED,
                ),
            )

            assertEquals(
                listOf(older.id, newer.id, undated.id),
                repositories.feedItems.findPending(limit = 10).map { it.id },
            )
            assertEquals(
                listOf(older.id),
                repositories.feedItems.findPending(limit = 1).map { it.id },
            )
            assertEquals(emptyList(), repositories.feedItems.findPending(limit = 0))
        }
    }

    @Test
    fun `findPending はフィードで絞れる`() {
        withRepositories { repositories ->
            val feed1 = repositories.addFeed(username = "feed1", url = "https://example.com/1.xml")
            val feed2 = repositories.addFeed(username = "feed2", url = "https://example.com/2.xml")
            val item1 = assertNotNull(
                repositories.feedItems.add(
                    item(feedId = feed1.id, itemKey = "a", state = FeedItemState.PENDING),
                ),
            )
            repositories.feedItems.add(
                item(feedId = feed2.id, itemKey = "b", state = FeedItemState.PENDING),
            )

            assertEquals(listOf(item1.id), repositories.feedItems.findPending(feed1.id, limit = 10).map { it.id })
        }
    }

    @Test
    fun `markPosted と markSkipped で状態が変わる`() {
        withRepositories { repositories ->
            val feed = repositories.addFeed()
            val pending = assertNotNull(
                repositories.feedItems.add(
                    item(feedId = feed.id, itemKey = "p", state = FeedItemState.PENDING),
                ),
            )
            repositories.notes.add(note(publicId = "note-1"))

            repositories.feedItems.markPosted(pending.id, POSTED_AT, noteId = "note-1")
            repositories.feedItems.markSkipped(
                assertNotNull(
                    repositories.feedItems.add(
                        item(feedId = feed.id, itemKey = "s", state = FeedItemState.PENDING),
                    ),
                ).id,
            )

            assertEquals(emptyList(), repositories.feedItems.findPending(limit = 10))
            assertEquals(2, repositories.feedItems.countByFeed(feed.id))
        }
    }

    @Test
    fun `findByNoteIds は投稿の id から記事を引く`() {
        withRepositories { repositories ->
            val feed = repositories.addFeed()
            val posted = assertNotNull(
                repositories.feedItems.add(item(feedId = feed.id, itemKey = "posted", state = FeedItemState.PENDING)),
            )
            repositories.feedItems.add(item(feedId = feed.id, itemKey = "pending", state = FeedItemState.PENDING))
            repositories.notes.add(note(publicId = "note-1"))
            repositories.feedItems.markPosted(posted.id, POSTED_AT, noteId = "note-1")

            val found = repositories.feedItems.findByNoteIds(listOf("note-1", "note-2"))

            assertEquals(setOf("note-1"), found.keys)
            assertEquals(posted.id, assertNotNull(found["note-1"]).id)
            assertEquals(emptyMap(), repositories.feedItems.findByNoteIds(emptyList()))
        }
    }

    @Test
    fun `記事を消しても投稿は残る`() {
        withRepositories { repositories ->
            val feed = repositories.addFeed()
            val posted = assertNotNull(
                repositories.feedItems.add(item(feedId = feed.id, itemKey = "posted", state = FeedItemState.PENDING)),
            )
            repositories.notes.add(note(publicId = "note-1"))
            repositories.feedItems.markPosted(posted.id, POSTED_AT, noteId = "note-1")

            repositories.feedItems.delete(posted.id)

            assertNotNull(repositories.notes.find("note-1"))
            assertEquals(emptyMap(), repositories.feedItems.findByNoteIds(listOf("note-1")))
        }
    }

    @Test
    fun `delete で記事だけ消える`() {
        withRepositories { repositories ->
            val feed = repositories.addFeed()
            val added = assertNotNull(repositories.feedItems.add(item(feedId = feed.id, itemKey = "gone")))

            assertTrue(repositories.feedItems.delete(added.id))

            assertNull(repositories.feedItems.find(added.id))
            assertEquals(0, repositories.feedItems.countByFeed(feed.id))
            assertNotNull(repositories.feeds.find(feed.id))
        }
    }

    @Test
    fun `消した記事は取り込み直すと新着になる`() {
        withRepositories { repositories ->
            val feed = repositories.addFeed()
            val added = assertNotNull(
                repositories.feedItems.add(item(feedId = feed.id, itemKey = "again", state = FeedItemState.POSTED)),
            )
            repositories.feedItems.delete(added.id)

            val reimported = assertNotNull(
                repositories.feedItems.add(item(feedId = feed.id, itemKey = "again", state = FeedItemState.PENDING)),
            )

            assertEquals(
                listOf(reimported.id),
                repositories.feedItems.findPending(feed.id, limit = 10).map { it.id },
            )
        }
    }

    @Test
    fun `投稿を消すと記事の紐付けが外れる`() {
        withRepositories { repositories ->
            val feed = repositories.addFeed()
            val posted = assertNotNull(
                repositories.feedItems.add(item(feedId = feed.id, itemKey = "posted", state = FeedItemState.PENDING)),
            )
            repositories.notes.add(note(publicId = "note-1"))
            repositories.feedItems.markPosted(posted.id, POSTED_AT, noteId = "note-1")

            repositories.notes.delete("note-1")

            // 記事は残るが、消えた投稿を指したままにはしない
            assertEquals(emptyMap(), repositories.feedItems.findByNoteIds(listOf("note-1")))
            assertNull(assertNotNull(repositories.feedItems.find(posted.id)).noteId)
        }
    }

    @Test
    fun `無い記事は消せない`() {
        withRepositories { repositories ->
            repositories.addFeed()

            assertEquals(false, repositories.feedItems.delete(FeedItemId(404)))
        }
    }

    @Test
    fun `フィードを消すと記事も消える`() {
        withRepositories { repositories ->
            val feed = repositories.addFeed()
            repositories.feedItems.add(item(feedId = feed.id, itemKey = "gone"))

            repositories.feeds.delete(feed.id)

            assertTrue(repositories.feedItems.findExistingKeys(feed.id, listOf("gone")).isEmpty())
            assertEquals(0, repositories.feedItems.countByFeed(feed.id))
        }
    }

    private fun Repositories.addFeed(
        username: String = "feed1",
        url: String = "https://example.com/feed.xml",
    ): Feed {
        val account = assertNotNull(accounts.add(username = username, createdAt = CREATED_AT))
        return assertNotNull(
            feeds.add(
                NewFeed(
                    accountId = account.id,
                    url = url,
                    title = null,
                    siteUrl = null,
                    format = null,
                    pollIntervalSeconds = 900,
                ),
            ),
        )
    }

    private fun item(
        feedId: FeedId,
        itemKey: String,
        publishedAt: Instant? = null,
        state: FeedItemState = FeedItemState.SKIPPED,
    ): NewFeedItem = NewFeedItem(
        feedId = feedId,
        itemKey = itemKey,
        title = "題名",
        link = "https://example.com/$itemKey",
        contentHtml = "<p>本文</p>",
        publishedAt = publishedAt,
        importedAt = CREATED_AT,
        state = state,
    )

    private fun note(publicId: String): NewNote = NewNote(
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

        val POSTED_AT: Instant = Instant.parse("2026-08-16T12:00:00Z")
    }
}
