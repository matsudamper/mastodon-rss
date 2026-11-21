package net.matsudamper.mastodon.rss.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import net.matsudamper.mastodon.rss.FakeFollowerRepository

class DeliveryTargetLimitServiceTest {
    @Test
    fun `上限は渡した値をそのまま返す`() {
        val service = DeliveryTargetLimitService(maxDeliveryTargetsPerHost = 20, followers = FakeFollowerRepository())

        assertEquals(20, service.maxDeliveryTargetsPerHost)
    }
}
