package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.mastodon.rss.FakeDomainBlockRepository
import net.matsudamper.mastodon.rss.repository.DomainBlockReason

class DomainBlockServiceTest {
    private val now: Instant = Instant.parse("2026-09-26T00:00:00Z")

    private val repository = FakeDomainBlockRepository()

    private val service = DomainBlockService(domainBlocks = repository, clock = { now })

    @Test
    fun `inbox の URL のホスト名を大文字小文字を揃えて見る`() {
        repository.markUnavailable(domain = "remote.example", description = "諦めた", at = now)

        assertTrue(service.blocksDeliveryTo("https://Remote.Example/inbox"))
        assertFalse(service.blocksDeliveryTo("https://other.example/inbox"))
        // 読めない URL は止めない
        assertFalse(service.blocksDeliveryTo("not a url"))
    }

    @Test
    fun `署名付きのリクエストが届いたら自動で止めたものだけ外す`() {
        repository.markUnavailable(domain = "remote.example", description = "諦めた", at = now)
        service.save(domain = "manual.example", blockDelivery = true, blockInbox = true, description = "")

        service.markAvailable("https://remote.example/users/alice")
        service.markAvailable("https://manual.example/users/bob")

        assertNull(repository.find("remote.example"))
        assertEquals(DomainBlockReason.MANUAL, repository.find("manual.example")?.reason)
    }

    @Test
    fun `管理画面の入力は URL ならホスト名を取り出して小文字に揃える`() {
        val saved = assertIs<DomainBlockService.SaveResult.Success>(
            service.save(domain = " https://Remote.Example/users/alice ", blockDelivery = true, blockInbox = false, description = "  "),
        )

        assertEquals("remote.example", saved.block.domain)
        // 空の説明は無しとして持つ
        assertNull(saved.block.reasonDescription)
        assertTrue(service.blocksDeliveryTo("https://remote.example/inbox"))
        assertFalse(service.blocksInboxFrom("https://remote.example/users/alice#main-key"))
    }

    @Test
    fun `ドメインとして読めない入力は保存しない`() {
        assertEquals(
            DomainBlockService.SaveResult.InvalidDomain,
            service.save(domain = "remote example", blockDelivery = true, blockInbox = true, description = ""),
        )
        assertEquals(
            DomainBlockService.SaveResult.InvalidDomain,
            service.save(domain = "", blockDelivery = true, blockInbox = true, description = ""),
        )
    }

    @Test
    fun `理由の説明が長すぎると保存しない`() {
        val result = service.save(
            domain = "remote.example",
            blockDelivery = true,
            blockInbox = true,
            description = "あ".repeat(DomainBlockService.DESCRIPTION_MAX_LENGTH + 1),
        )

        assertEquals(DomainBlockService.SaveResult.DescriptionTooLong(DomainBlockService.DESCRIPTION_MAX_LENGTH), result)
    }

    @Test
    fun `外すと外したドメインを返し 止めていなければ null`() {
        service.save(domain = "remote.example", blockDelivery = true, blockInbox = true, description = "")

        assertEquals("remote.example", service.delete("Remote.Example"))
        assertNull(service.delete("remote.example"))
    }
}
