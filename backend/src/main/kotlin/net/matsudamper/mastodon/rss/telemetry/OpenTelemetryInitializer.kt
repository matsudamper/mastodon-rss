package net.matsudamper.mastodon.rss.telemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetry
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

        val configured = builder.build()
        val sdk = configured.getOpenTelemetrySdk()
        val runtimeTelemetry = RuntimeTelemetry.create(sdk)

        return Handler(
            openTelemetry = sdk,
            sdk = sdk,
            runtimeTelemetry = runtimeTelemetry,
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
        private val runtimeTelemetry: RuntimeTelemetry,
    ) : AutoCloseable {
        override fun close() {
            runtimeTelemetry.close()
            sdk.close()
        }
    }
}
