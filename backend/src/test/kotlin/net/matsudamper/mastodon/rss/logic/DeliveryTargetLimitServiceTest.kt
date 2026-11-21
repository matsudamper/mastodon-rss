package net.matsudamper.mastodon.rss.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import net.matsudamper.mastodon.rss.FakeFollowerRepository
import net.matsudamper.mastodon.rss.FakeServerSettingRepository

class DeliveryTargetLimitServiceTest {
    private val settings = FakeServerSettingRepository()

    private val service = DeliveryTargetLimitService(settings = settings, followers = FakeFollowerRepository())

    @Test
    fun `保存していなければ上限は 20`() {
        assertEquals(20, service.maxDeliveryTargetsPerHost())
    }

    @Test
    fun `保存した上限が次の読み出しから効く`() {
        assertIs<DeliveryTargetLimitService.SaveResult.Success>(service.save(50))

        assertEquals(50, service.maxDeliveryTargetsPerHost())
    }

    @Test
    fun `範囲の外の上限は保存しない`() {
        val tooSmall = service.save(0)
        val tooLarge = service.save(1001)

        assertEquals(DeliveryTargetLimitService.SaveResult.OutOfRange(minValue = 1, maxValue = 1000), tooSmall)
        assertEquals(DeliveryTargetLimitService.SaveResult.OutOfRange(minValue = 1, maxValue = 1000), tooLarge)
        assertNull(settings.maxDeliveryTargetsPerHost())
    }
}
