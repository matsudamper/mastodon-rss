package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.matsudamper.kotpub.TestActorPrivateKey
import net.matsudamper.kotpub.TestLocalActor
import net.matsudamper.kotpub.TestRemoteActor
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.repository.DeliveryKind

class QueuedQuoteApprovalsTest {
    private val now: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val repositories = FakeRepositories()
    private val domainBlocks = DomainBlockService(domainBlocks = repositories.domainBlocks, clock = { now })
    private val approvals = QueuedQuoteApprovals(
        directory = TestLocalActor.directory,
        actorKey = TestActorPrivateKey.value,
        domainBlocks = domainBlocks,
        deliveryQueue = repositories.deliveryQueue,
        clock = { now },
    )

    @Test
    fun `承認の Accept は引用した相手の inbox 宛てに投函される`() {
        approvals.enqueueAccept(username = TestLocalActor.USERNAME, quoter = TestRemoteActor.actor, acceptBody = """{"type":"Accept"}""")

        val claimed = repositories.deliveryQueue.claim(now = now, limit = 10).single()
        assertEquals(DeliveryKind.ACCEPT_QUOTE, claimed.kind)
        assertEquals(TestLocalActor.USERNAME, claimed.username)
        assertEquals(TestRemoteActor.INBOX, claimed.inbox)
        assertEquals("""{"type":"Accept"}""", claimed.body)
    }

    @Test
    fun `受信を止めているドメインの相手の引用は認めない`() {
        assertFalse(approvals.refuses(username = TestLocalActor.USERNAME, quoterActorId = TestRemoteActor.ACTOR_ID))

        domainBlocks.save(domain = "remote.example", blockDelivery = false, blockInbox = true, description = "")

        assertTrue(approvals.refuses(username = TestLocalActor.USERNAME, quoterActorId = TestRemoteActor.ACTOR_ID))
    }
}
