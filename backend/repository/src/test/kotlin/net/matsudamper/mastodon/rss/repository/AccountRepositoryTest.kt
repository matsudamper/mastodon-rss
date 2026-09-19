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
import net.matsudamper.mastodon.rss.shared.PublicNoteId

class AccountRepositoryTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-account-test")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `追加したアカウントを引ける`() {
        withRepositories { repositories ->
            val added = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))

            assertEquals("feed1", added.username)
            assertEquals(CREATED_AT, added.createdAt)
            assertEquals(added, repositories.accounts.findByUsername("feed1"))
        }
    }

    @Test
    fun `プロフィールを書き換えると引き直したときに反映されている`() {
        withRepositories { repositories ->
            val added = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))

            val updated = assertNotNull(
                repositories.accounts.updateProfile(id = added.id, displayName = "フィード 1", summary = "説明"),
            )

            assertEquals("フィード 1", updated.displayName)
            assertEquals("説明", updated.summary)
            assertEquals(updated, repositories.accounts.findByUsername("feed1"))
        }
    }

    @Test
    fun `プロフィールに null を渡すと未設定に戻る`() {
        withRepositories { repositories ->
            val added = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            repositories.accounts.updateProfile(id = added.id, displayName = "フィード 1", summary = "説明")

            val cleared = assertNotNull(
                repositories.accounts.updateProfile(id = added.id, displayName = null, summary = null),
            )

            assertNull(cleared.displayName)
            assertNull(cleared.summary)
        }
    }

    @Test
    fun `大文字小文字の違いは同じ名前として扱う`() {
        withRepositories { repositories ->
            repositories.accounts.add(username = "Feed1", createdAt = CREATED_AT)

            // 引くときも入れるときも同じ扱いにしないと、引けないアカウントが増える
            assertEquals("Feed1", repositories.accounts.findByUsername("feed1")?.username)
            assertNull(repositories.accounts.add(username = "FEED1", createdAt = CREATED_AT))
        }
    }

    @Test
    fun `複数アカウントをまとめて引ける`() {
        withRepositories { repositories ->
            repositories.accounts.add(username = "Feed1", createdAt = CREATED_AT)
            repositories.accounts.add(username = "gihyo", createdAt = CREATED_AT)

            val result = repositories.accounts.findByUsernames(listOf("feed1", "GIHYO", "other"))
            assertEquals(2, result.size)
            assertEquals("Feed1", result["feed1"]?.username)
            assertEquals("gihyo", result["GIHYO"]?.username)
            assertNull(result["other"])
        }
    }

    @Test
    fun `一覧は追加した順に返る`() {
        withRepositories { repositories ->
            listOf("gihyo", "feed1", "blog").forEach {
                repositories.accounts.add(username = it, createdAt = CREATED_AT)
            }

            assertEquals(
                listOf("gihyo", "feed1", "blog"),
                repositories.accounts.list().map { it.username },
            )
        }
    }

    @Test
    fun `秒未満の桁が違っても時刻の順に返る`() {
        withRepositories { repositories ->
            // 文字列で保存しているので、桁が揃っていないと並びが時刻の順にならない
            repositories.accounts.add(username = "late", createdAt = Instant.parse("2026-08-16T00:00:00.5Z"))
            repositories.accounts.add(username = "early", createdAt = Instant.parse("2026-08-16T00:00:00Z"))

            assertEquals(listOf("early", "late"), repositories.accounts.list().map { it.username })
        }
    }

    @Test
    fun `カーソルを指定してページングで取得できる`() {
        withRepositories { repositories ->
            listOf("a", "b", "c", "d", "e").forEachIndexed { index, username ->
                repositories.accounts.add(username = username, createdAt = CREATED_AT.plusSeconds(index.toLong()))
            }

            val page1 = repositories.accounts.list(after = null, limit = 2)
            assertEquals(listOf("a", "b"), page1.map { it.username })

            val page2 = repositories.accounts.list(after = page1.last().position(), limit = 2)
            assertEquals(listOf("c", "d"), page2.map { it.username })

            val page3 = repositories.accounts.list(after = page2.last().position(), limit = 2)
            assertEquals(listOf("e"), page3.map { it.username })

            val page4 = repositories.accounts.list(after = page3.last().position(), limit = 2)
            assertEquals(emptyList(), page4.map { it.username })
        }
    }

    @Test
    fun `時刻が同じでもページングで重複や取りこぼしが出ない`() {
        withRepositories { repositories ->
            listOf("a", "b", "c", "d", "e").forEach { username ->
                repositories.accounts.add(username = username, createdAt = CREATED_AT)
            }

            val page1 = repositories.accounts.list(after = null, limit = 2)
            assertEquals(listOf("a", "b"), page1.map { it.username })

            val page2 = repositories.accounts.list(after = page1.last().position(), limit = 2)
            assertEquals(listOf("c", "d"), page2.map { it.username })

            val page3 = repositories.accounts.list(after = page2.last().position(), limit = 2)
            assertEquals(listOf("e"), page3.map { it.username })

            val page4 = repositories.accounts.list(after = page3.last().position(), limit = 2)
            assertEquals(emptyList(), page4.map { it.username })
        }
    }

    @Test
    fun `カーソルの指す行が消えていても続きが返る`() {
        withRepositories { repositories ->
            listOf("a", "b", "c", "d").forEachIndexed { index, username ->
                repositories.accounts.add(username = username, createdAt = CREATED_AT.plusSeconds(index.toLong()))
            }

            val page1 = repositories.accounts.list(after = null, limit = 2)
            val after = page1.last().position()
            repositories.deleteAccount(page1.last())

            assertEquals(listOf("c", "d"), repositories.accounts.list(after = after, limit = 2).map { it.username })
        }
    }

    @Test
    fun `消した名前は作り直せない`() {
        withRepositories { repositories ->
            val account = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            repositories.deleteAccount(account)

            // 作り直せると、送り残した Delete{Actor} が新しいアカウントのものとして配られる
            assertNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT.plusSeconds(10)))
            assertNull(repositories.accounts.findByUsername("feed1"))
        }
    }

    @Test
    fun `開き直しても残っている`() {
        val dbPath = tempDir.resolve("test.db")
        TestSchema.applyTo(dbPath)
        val config = DatabaseConfig(path = dbPath)

        createRepositories(config).use { it.accounts.add(username = "feed1", createdAt = CREATED_AT) }

        createRepositories(config).use {
            assertEquals(CREATED_AT, it.accounts.findByUsername("feed1")?.createdAt)
        }
    }

    @Test
    fun `消すとフィードと記事も一緒に消える`() {
        withRepositories { repositories ->
            val account = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            val feed = assertNotNull(
                repositories.feeds.add(
                    NewFeed(
                        accountId = account.id,
                        url = FEED_URL,
                        title = "サンプル",
                        siteUrl = "https://example.com/",
                        format = "RSS 2.0",
                        iconUrl = null,
                        pollIntervalSeconds = POLL_INTERVAL_SECONDS,
                    ),
                ),
            )
            repositories.feedItems.add(
                NewFeedItem(
                    feedId = feed.id,
                    itemKey = "item-1",
                    title = "1 本目",
                    link = "https://example.com/1",
                    contentHtml = "<p>記事</p>",
                    publishedAt = CREATED_AT,
                    importedAt = CREATED_AT,
                    state = FeedItemState.PENDING,
                ),
            )

            assertNotNull(repositories.deleteAccount(account))

            assertNull(repositories.accounts.findById(account.id))
            // 消えていないと、同じフィードを登録し直せない
            assertNull(repositories.feeds.findByUrl(FEED_URL))
            assertEquals(0L, repositories.feedItems.countByFeed(feed.id))
        }
    }

    @Test
    fun `消えているアカウントを消しても何も起きない`() {
        withRepositories { repositories ->
            val account = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            repositories.deleteAccount(account)

            // 2 回目も投函すると、同じ削除が 2 度配られる
            assertNull(repositories.deleteAccount(account))
        }
    }

    @Test
    fun `消すと宛先ごとに Delete が投函され 送り残した配信は消える`() {
        withRepositories { repositories ->
            val account = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            repositories.deliveryQueue.enqueueNote(
                NotePost(
                    note = NewNote(
                        username = "feed1",
                        publicId = PublicNoteId("note-1"),
                        contentHtml = "<p>本文</p>",
                        publishedAt = CREATED_AT,
                    ),
                    body = """{"type":"Create"}""",
                    inboxes = listOf(INBOX),
                    enqueuedAt = CREATED_AT,
                    feedItemId = null,
                ),
            )

            val deleted = assertNotNull(repositories.deleteAccount(account, inboxes = listOf(INBOX)))

            assertEquals(1, deleted.deletedNotes)
            assertEquals(1, deleted.deliveries)
            // 消えたアカウントの投稿が後から届かないよう、送り残しは消える
            val claimed = repositories.deliveryQueue.claim(now = DELETED_AT, limit = 10)
            assertEquals(listOf(DeliveryKind.DELETE_ACTOR), claimed.map { it.kind })
            assertNull(repositories.notes.find(PublicNoteId("note-1")))
        }
    }

    @Test
    fun `配信を送り切った削除済みアカウントだけが片付く`() {
        withRepositories { repositories ->
            val sending = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            val done = assertNotNull(repositories.accounts.add(username = "feed2", createdAt = CREATED_AT))
            repositories.deleteAccount(sending, inboxes = listOf(INBOX))
            repositories.deleteAccount(done)

            assertEquals(1, repositories.accounts.purgeDeleted())

            // 送り切るまでは名前を押さえたままにする
            assertNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            assertNotNull(repositories.accounts.add(username = "feed2", createdAt = CREATED_AT))
        }
    }

    /**
     * アカウントを消して `Delete{Actor}` を投函する
     */
    private fun Repositories.deleteAccount(
        account: Account,
        inboxes: List<String> = emptyList(),
    ): AccountDeletionResult? = accounts.markDeleted(
        AccountDeletion(
            id = account.id,
            username = account.username,
            body = """{"type":"Delete"}""",
            inboxes = inboxes,
            deletedAt = DELETED_AT,
        ),
    )

    private fun withRepositories(block: (Repositories) -> Unit) {
        val dbPath = tempDir.resolve("test.db")
        TestSchema.applyTo(dbPath)

        createRepositories(DatabaseConfig(path = dbPath)).use(block)
    }

    private companion object {
        const val FEED_URL = "https://example.com/feed.xml"
        const val INBOX = "https://remote.example/inbox"
        const val POLL_INTERVAL_SECONDS = 900L

        // 秒未満まで持つ。文字列で保存しているので、桁が落ちるとここで分かる
        val CREATED_AT: Instant = Instant.parse("2026-08-16T01:02:03.123456Z")

        val DELETED_AT: Instant = CREATED_AT.plusSeconds(60)
    }
}
