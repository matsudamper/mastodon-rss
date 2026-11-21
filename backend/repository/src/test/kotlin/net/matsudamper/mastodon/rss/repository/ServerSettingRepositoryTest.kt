package net.matsudamper.mastodon.rss.repository

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class ServerSettingRepositoryTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-server-setting-test")

    private val dbPath: Path = tempDir.resolve("test.db")

    init {
        TestSchema.applyTo(dbPath)
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun <T> withRepository(block: (ServerSettingRepository) -> T): T =
        createRepositories(DatabaseConfig(path = dbPath)).use { block(it.serverSettings) }

    @Test
    fun `保存していない配信先の上限は null`() {
        withRepository { settings ->
            assertNull(settings.maxDeliveryTargetsPerHost())
        }
    }

    @Test
    fun `配信先の上限は保存し直すと置き換わり開き直しても残る`() {
        withRepository { settings ->
            settings.saveMaxDeliveryTargetsPerHost(30)
            settings.saveMaxDeliveryTargetsPerHost(50)
        }

        withRepository { settings ->
            assertEquals(50, settings.maxDeliveryTargetsPerHost())
        }
    }

    @Test
    fun `配信先の上限に 0 は保存できない`() {
        withRepository { settings ->
            assertFails { settings.saveMaxDeliveryTargetsPerHost(0) }
            assertNull(settings.maxDeliveryTargetsPerHost())
        }
    }
}
