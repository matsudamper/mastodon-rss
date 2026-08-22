package net.matsudamper.mastodon.rss.repository

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountProfileRepositoryTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-account-profile-test")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `保存したプロフィールを引ける`() {
        withRepositories { repositories ->
            val saved = repositories.accountProfiles.upsert(
                username = "feed1",
                displayName = "フィード 1",
                summary = "説明文",
            )

            assertEquals("feed1", saved.username)
            assertEquals("フィード 1", saved.displayName)
            assertEquals("説明文", saved.summary)
            assertEquals(saved, repositories.accountProfiles.findByUsername("feed1"))
        }
    }

    @Test
    fun `大文字小文字の違いは同じ名前として扱う`() {
        withRepositories { repositories ->
            repositories.accountProfiles.upsert(
                username = "Feed1",
                displayName = "表示名",
                summary = "説明",
            )

            assertEquals("Feed1", repositories.accountProfiles.findByUsername("feed1")?.username)
        }
    }

    @Test
    fun `同じ名前で保存すると上書きされる`() {
        withRepositories { repositories ->
            repositories.accountProfiles.upsert(
                username = "feed1",
                displayName = "古い名前",
                summary = "古い説明",
            )
            repositories.accountProfiles.upsert(
                username = "feed1",
                displayName = "新しい名前",
                summary = "新しい説明",
            )

            val profile = repositories.accountProfiles.findByUsername("feed1")
            assertEquals("新しい名前", profile?.displayName)
            assertEquals("新しい説明", profile?.summary)
        }
    }

    @Test
    fun `複数アカウントをまとめて引ける`() {
        withRepositories { repositories ->
            repositories.accountProfiles.upsert(
                username = "Feed1",
                displayName = "表示名 1",
                summary = "説明 1",
            )
            repositories.accountProfiles.upsert(
                username = "gihyo",
                displayName = "表示名 2",
                summary = "説明 2",
            )

            val result = repositories.accountProfiles.findByUsernames(listOf("feed1", "GIHYO", "other"))
            assertEquals(2, result.size)
            assertEquals("表示名 1", result["feed1"]?.displayName)
            assertEquals("説明 2", result["GIHYO"]?.summary)
            assertNull(result["other"])
        }
    }

    @Test
    fun `開き直しても残っている`() {
        val dbPath = tempDir.resolve("test.db")
        TestSchema.applyTo(dbPath)
        val config = DatabaseConfig(path = dbPath)

        createRepositories(config).use {
            it.accountProfiles.upsert(
                username = "feed1",
                displayName = "表示名",
                summary = "説明",
            )
        }

        createRepositories(config).use {
            val profile = it.accountProfiles.findByUsername("feed1")
            assertEquals("表示名", profile?.displayName)
            assertEquals("説明", profile?.summary)
        }
    }

    private fun withRepositories(block: (Repositories) -> Unit) {
        val dbPath = tempDir.resolve("test.db")
        TestSchema.applyTo(dbPath)

        createRepositories(DatabaseConfig(path = dbPath)).use(block)
    }
}
