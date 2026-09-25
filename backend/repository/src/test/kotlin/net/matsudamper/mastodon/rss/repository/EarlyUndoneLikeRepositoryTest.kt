package net.matsudamper.mastodon.rss.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.sqlite.SQLiteDataSource

class EarlyUndoneLikeRepositoryTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-early-undone-like-test")

    private val dbPath: Path = tempDir.resolve("test.db")

    private val actorUri = "https://remote.example/users/alice"

    private val likeUri = "https://remote.example/likes/1"

    init {
        TestSchema.applyTo(dbPath)
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun <T> withRepository(block: (EarlyUndoneLikeRepository) -> T): T =
        createRepositories(DatabaseConfig(path = dbPath)).use { block(it.earlyUndoneLikes) }

    /**
     * 期限切れの行が残っているかは口が無いので、DB を直接見る
     */
    private fun rowCount(): Int {
        val dataSource = SQLiteDataSource().apply { url = "jdbc:sqlite:$dbPath" }
        return dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM early_undone_likes").use { result ->
                    result.next()
                    result.getInt(1)
                }
            }
        }
    }

    @Test
    fun `覚えた組は期限まで引ける`() {
        withRepository { repository ->
            val now = Instant.now()
            repository.remember(actorUri = actorUri, activityUri = likeUri, expiresAt = now.plusSeconds(60))

            assertTrue(repository.isRemembered(actorUri = actorUri, activityUri = likeUri, now = now))
            assertFalse(repository.isRemembered(actorUri = actorUri, activityUri = likeUri, now = now.plusSeconds(61)))
        }
    }

    @Test
    fun `別の相手の組としては引けない`() {
        withRepository { repository ->
            val now = Instant.now()
            repository.remember(actorUri = actorUri, activityUri = likeUri, expiresAt = now.plusSeconds(60))

            assertFalse(
                repository.isRemembered(actorUri = "https://remote.example/users/bob", activityUri = likeUri, now = now),
            )
        }
    }

    @Test
    fun `覚え直すと期限が延びる`() {
        withRepository { repository ->
            val now = Instant.now()
            repository.remember(actorUri = actorUri, activityUri = likeUri, expiresAt = now.plusSeconds(60))
            repository.remember(actorUri = actorUri, activityUri = likeUri, expiresAt = now.plusSeconds(120))

            assertTrue(repository.isRemembered(actorUri = actorUri, activityUri = likeUri, now = now.plusSeconds(90)))
        }
    }

    @Test
    fun `覚えるときに期限切れの行を片付ける`() {
        withRepository { repository ->
            repository.remember(actorUri = actorUri, activityUri = likeUri, expiresAt = Instant.now().minusSeconds(1))
            repository.remember(
                actorUri = actorUri,
                activityUri = "https://remote.example/likes/2",
                expiresAt = Instant.now().plusSeconds(60),
            )

            assertEquals(1, rowCount())
        }
    }
}
