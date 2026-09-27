package net.matsudamper.mastodon.rss.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainBlockRepositoryTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-domain-block-test")

    private val dbPath: Path = tempDir.resolve("test.db")

    private val now: Instant = Instant.parse("2026-09-26T00:00:00Z")

    init {
        TestSchema.applyTo(dbPath)
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun <T> withRepository(block: (DomainBlockRepository) -> T): T =
        createRepositories(DatabaseConfig(path = dbPath)).use { block(it.domainBlocks) }

    @Test
    fun `配信を諦めて止めたドメインは配信だけを止める`() {
        withRepository { repository ->
            assertTrue(repository.markUnavailable(domain = DOMAIN, description = "諦めた", at = now))

            assertTrue(repository.blocksDelivery(DOMAIN))
            assertFalse(repository.blocksInbox(DOMAIN))
            val block = assertNotNull(repository.find(DOMAIN))
            assertEquals(DomainBlockReason.UNAVAILABLE, block.reason)
            assertEquals("諦めた", block.reasonDescription)
            assertEquals(now, block.createdAt)
        }
    }

    @Test
    fun `手で止めたドメインは配信を諦めても上書きしない`() {
        withRepository { repository ->
            repository.saveManual(domain = DOMAIN, blockDelivery = false, blockInbox = true, description = "手で止めた", at = now)

            assertFalse(repository.markUnavailable(domain = DOMAIN, description = "諦めた", at = now))

            val block = assertNotNull(repository.find(DOMAIN))
            assertEquals(DomainBlockReason.MANUAL, block.reason)
            assertFalse(block.blockDelivery)
            assertEquals("手で止めた", block.reasonDescription)
        }
    }

    @Test
    fun `自動で外すのは配信を諦めて止めたものだけ`() {
        withRepository { repository ->
            repository.markUnavailable(domain = DOMAIN, description = "諦めた", at = now)
            repository.saveManual(domain = OTHER_DOMAIN, blockDelivery = true, blockInbox = true, description = null, at = now)

            assertTrue(repository.clearUnavailable(DOMAIN))
            assertFalse(repository.clearUnavailable(OTHER_DOMAIN))

            assertNull(repository.find(DOMAIN))
            assertNotNull(repository.find(OTHER_DOMAIN))
        }
    }

    @Test
    fun `自動で止めたものを手で保存すると手動になり止め始めた時刻は残る`() {
        withRepository { repository ->
            repository.markUnavailable(domain = DOMAIN, description = "諦めた", at = now)

            val saved = repository.saveManual(
                domain = DOMAIN,
                blockDelivery = true,
                blockInbox = true,
                description = "手で止めた",
                at = now.plusSeconds(60),
            )

            assertEquals(DomainBlockReason.MANUAL, saved.reason)
            assertTrue(saved.blockInbox)
            assertEquals("手で止めた", saved.reasonDescription)
            assertEquals(now, saved.createdAt)
            assertFalse(repository.clearUnavailable(DOMAIN))
        }
    }

    @Test
    fun `一覧はドメインの順に位置から続きを返す`() {
        withRepository { repository ->
            listOf("c.example", "a.example", "b.example").forEach { domain ->
                repository.markUnavailable(domain = domain, description = "諦めた", at = now)
            }

            assertEquals(listOf("a.example", "b.example"), repository.list(afterDomain = null, limit = 2).map { it.domain })
            assertEquals(listOf("c.example"), repository.list(afterDomain = "b.example", limit = 2).map { it.domain })
        }
    }

    @Test
    fun `理由を問わず削除できる`() {
        withRepository { repository ->
            repository.saveManual(domain = DOMAIN, blockDelivery = true, blockInbox = true, description = null, at = now)

            assertTrue(repository.delete(DOMAIN))
            assertFalse(repository.delete(DOMAIN))
            assertFalse(repository.blocksDelivery(DOMAIN))
        }
    }

    private companion object {
        const val DOMAIN = "remote.example"
        const val OTHER_DOMAIN = "other.example"
    }
}
