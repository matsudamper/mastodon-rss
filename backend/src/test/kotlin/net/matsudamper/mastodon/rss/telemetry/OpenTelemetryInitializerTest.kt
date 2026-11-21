package net.matsudamper.mastodon.rss.telemetry

import kotlin.test.Test
import kotlin.test.assertFailsWith
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
    fun `ENABLE_OTEL が false なら起動しない`() {
        assertNull(OpenTelemetryInitializer.start(mapOf("ENABLE_OTEL" to "false")))
    }

    @Test
    fun `ENABLE_OTEL が未設定なら落ちる`() {
        assertFailsWith<IllegalArgumentException> { OpenTelemetryInitializer.start(mapOf()) }
        assertFailsWith<IllegalArgumentException> { OpenTelemetryInitializer.start(mapOf("ENABLE_OTEL" to " ")) }
    }

    @Test
    fun `ENABLE_OTEL が true でも false でもなければ落ちる`() {
        assertFailsWith<IllegalArgumentException> { OpenTelemetryInitializer.start(mapOf("ENABLE_OTEL" to "yes")) }
    }
}
