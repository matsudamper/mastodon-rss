package net.matsudamper.mastodon.rss.telemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk

object OpenTelemetryInitializer {

    fun start(env: Map<String, String> = System.getenv()): Handler? {
        if (env["ENABLE_OTEL"]?.toBooleanStrictOrNull() != true) return null

        val builder =
            AutoConfiguredOpenTelemetrySdk.builder()
                .disableShutdownHook()
                .addPropertiesSupplier {
                    mapOf(
                        "otel.exporter.otlp.protocol" to "http/protobuf",
                    )
                }
                .addResourceCustomizer { oldResource, _ ->
                    oldResource
                }

        val sdk = builder.build()

        return Handler(
            openTelemetry = sdk.openTelemetrySdk,
            sdk = sdk.openTelemetrySdk,
        )
    }

    /**
     * OpenTelemetry SDK を手動で起動する。
     *
     * GraalVM native-image では javaagent が使えないため、
     * [AutoConfiguredOpenTelemetrySdk] で環境変数から設定を読む。
     * ログは対応しない。トレースとメトリクスのみ。
     */
    class Handler(
        val openTelemetry: OpenTelemetry,
        private val sdk: OpenTelemetrySdk,
    ) : AutoCloseable {
        override fun close() {
            sdk.close()
        }
    }
}
