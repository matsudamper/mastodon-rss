package net.matsudamper.mastodon.rss.telemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk

object OpenTelemetryInitializer {

    fun start(env: Map<String, String> = System.getenv()): Handler? {
        if (isDisabled(env)) return null
        if (!isExportEnabled(env)) return null

        val builder =
            AutoConfiguredOpenTelemetrySdk.builder()
                .disableShutdownHook()
                .addResourceCustomizer { oldResource, _ ->
                    oldResource
                }

        val sdk = builder.build()

        return Handler(
            openTelemetry = sdk.openTelemetrySdk,
            sdk = sdk.openTelemetrySdk,
        )
    }

    private fun isDisabled(env: Map<String, String>): Boolean =
        env["OTEL_SDK_DISABLED"]?.trim()?.lowercase() == "true"

    private fun isExportEnabled(env: Map<String, String>): Boolean {
        if (!env["OTEL_EXPORTER_OTLP_ENDPOINT"].isNullOrBlank()) return true

        return listOf(
            exporterValue(env, "OTEL_TRACES_EXPORTER"),
            exporterValue(env, "OTEL_METRICS_EXPORTER"),
        ).any { it != "none" }
    }

    private fun exporterValue(
        env: Map<String, String>,
        envKey: String,
    ): String = env[envKey]?.trim()?.lowercase() ?: "none"

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
