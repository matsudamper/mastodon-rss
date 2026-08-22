package net.matsudamper.mastodon.rss.telemetry

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OpenTelemetryBootstrapTest {
    @Test
    fun `エクスポート設定が無いときは起動しない`() {
        assertNull(OpenTelemetryBootstrap.start(emptyMap()))
    }

    @Test
    fun `OTEL_SDK_DISABLEDがtrueのときは起動しない`() {
        assertNull(
            OpenTelemetryBootstrap.start(
                mapOf(
                    "OTEL_SDK_DISABLED" to "true",
                    "OTEL_TRACES_EXPORTER" to "otlp",
                ),
            ),
        )
    }

    @Test
    fun `OTEL_TRACES_EXPORTERがotlpのときは起動する`() {
        OpenTelemetryBootstrap.start(mapOf("OTEL_TRACES_EXPORTER" to "otlp")).use { handle ->
            assertNotNull(handle)
        }
    }
}
