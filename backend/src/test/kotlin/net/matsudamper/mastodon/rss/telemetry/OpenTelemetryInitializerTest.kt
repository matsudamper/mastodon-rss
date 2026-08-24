package net.matsudamper.mastodon.rss.telemetry

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OpenTelemetryInitializerTest {
    @Test
    fun `ENABLE_OTEL が true のとき SDK を起動できる`() {
        OpenTelemetryInitializer.start(mapOf("ENABLE_OTEL" to "true")).use { handler ->
            assertNotNull(handler)
            assertNotNull(handler.openTelemetry)
        }
    }

    @Test
    fun `ENABLE_OTEL が未設定なら起動しない`() {
        assertNull(OpenTelemetryInitializer.start(emptyMap()))
    }
}
