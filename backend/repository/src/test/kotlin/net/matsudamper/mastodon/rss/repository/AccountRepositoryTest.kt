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

            val page1 = repositories.accounts.list(afterUsername = null, limit = 2)
            assertEquals(listOf("a", "b"), page1.map { it.username })

            val page2 = repositories.accounts.list(afterUsername = page1.last().username, limit = 2)
            assertEquals(listOf("c", "d"), page2.map { it.username })

            val page3 = repositories.accounts.list(afterUsername = page2.last().username, limit = 2)
            assertEquals(listOf("e"), page3.map { it.username })

            val page4 = repositories.accounts.list(afterUsername = page3.last().username, limit = 2)
            assertEquals(emptyList(), page4.map { it.username })
        }
    }

    @Test
    fun `時刻が同じでもページングで重複や取りこぼしが出ない`() {
        withRepositories { repositories ->
            listOf("a", "b", "c", "d", "e").forEach { username ->
                repositories.accounts.add(username = username, createdAt = CREATED_AT)
            }

            val page1 = repositories.accounts.list(afterUsername = null, limit = 2)
            assertEquals(listOf("a", "b"), page1.map { it.username })

            val page2 = repositories.accounts.list(afterUsername = page1.last().username, limit = 2)
            assertEquals(listOf("c", "d"), page2.map { it.username })

            val page3 = repositories.accounts.list(afterUsername = page2.last().username, limit = 2)
            assertEquals(listOf("e"), page3.map { it.username })

            val page4 = repositories.accounts.list(afterUsername = page3.last().username, limit = 2)
            assertEquals(emptyList(), page4.map { it.username })
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

    private fun withRepositories(block: (Repositories) -> Unit) {
        val dbPath = tempDir.resolve("test.db")
        TestSchema.applyTo(dbPath)

        createRepositories(DatabaseConfig(path = dbPath)).use(block)
    }

    private companion object {
        // 秒未満まで持つ。文字列で保存しているので、桁が落ちるとここで分かる
        val CREATED_AT: Instant = Instant.parse("2026-08-16T01:02:03.123456Z")
    }
}
