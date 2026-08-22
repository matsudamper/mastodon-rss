package net.matsudamper.mastodon.rss.telemetry // pragma: allowlist secret

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import io.opentelemetry.semconv.ServiceAttributes

/**
 * OpenTelemetry SDK を手動で起動する。
 *
 * GraalVM native-image では javaagent が使えないため、
 * [AutoConfiguredOpenTelemetrySdk] で環境変数から設定を読む。
 * ログは対応しない。トレースとメトリクスのみ。
 */
class OpenTelemetryHandle(
    val openTelemetry: OpenTelemetry,
    private val sdk: OpenTelemetrySdk,
) : AutoCloseable {
    override fun close() {
        sdk.close()
    }
}

object OpenTelemetryBootstrap {
    private const val SERVICE_NAME = "mastodon-rss"

    private val ENV_TO_PROPERTY =
        mapOf(
            "OTEL_TRACES_EXPORTER" to "otel.traces.exporter",
            "OTEL_METRICS_EXPORTER" to "otel.metrics.exporter",
            "OTEL_LOGS_EXPORTER" to "otel.logs.exporter",
            "OTEL_EXPORTER_OTLP_ENDPOINT" to "otel.exporter.otlp.endpoint",
            "OTEL_EXPORTER_OTLP_PROTOCOL" to "otel.exporter.otlp.protocol",
            "OTEL_SERVICE_NAME" to "otel.service.name",
        )

    /**
     * 環境変数が無いときはエクスポートしない。
     * 未設定のまま OTLP へ送ろうとすると、コレクターが無い環境で
     * 接続エラーが出続ける。
     */
    fun start(env: Map<String, String> = System.getenv()): OpenTelemetryHandle? {
        if (isDisabled(env)) return null
        if (!isExportEnabled(env)) return null

        val builder =
            AutoConfiguredOpenTelemetrySdk.builder()
                .disableShutdownHook()
                .addResourceCustomizer { oldResource, _ ->
                    if (oldResource.getAttribute(ServiceAttributes.SERVICE_NAME) != null) {
                        oldResource
                    } else {
                        oldResource.toBuilder()
                            .put(ServiceAttributes.SERVICE_NAME, SERVICE_NAME)
                            .build()
                    }
                }
                .addPropertiesCustomizer { config ->
                    buildMap {
                        if (config.getString("otel.traces.exporter") == null) {
                            put("otel.traces.exporter", "none")
                        }
                        if (config.getString("otel.metrics.exporter") == null) {
                            put("otel.metrics.exporter", "none")
                        }
                        if (config.getString("otel.logs.exporter") == null) {
                            put("otel.logs.exporter", "none")
                        }
                    }
                }

        if (env !== System.getenv()) {
            builder.addPropertiesSupplier { otelPropertiesFromEnv(env) }
        }

        val sdk = builder.build()

        return OpenTelemetryHandle(
            openTelemetry = sdk.openTelemetrySdk,
            sdk = sdk.openTelemetrySdk,
        )
    }

    private fun isDisabled(env: Map<String, String>): Boolean =
        env["ENABLE_OTEL"]?.trim()?.lowercase() == "true"

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

    private fun otelPropertiesFromEnv(env: Map<String, String>): Map<String, String> =
        buildMap {
            for ((envKey, property) in ENV_TO_PROPERTY) {
                val value = env[envKey]?.trim()
                if (!value.isNullOrEmpty()) {
                    put(property, value)
                }
            }
        }
}
