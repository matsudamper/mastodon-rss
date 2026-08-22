package net.matsudamper.mastodon.rss.telemetry // pragma: allowlist secret

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OpenTelemetryBootstrapTest {
    @Test
    fun `エクスポート設定が無いときは起動しない`() {
        assertNull(OpenTelemetryBootstrap.start(emptyMap()))
    }

    @Test
    fun `ENABLE_OTELがtrueのときは起動しない`() {
        assertNull(
            OpenTelemetryBootstrap.start(
                mapOf(
                    "ENABLE_OTEL" to "true",
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
