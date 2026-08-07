package dev.matsudamper.mastodonrss

import kotlinx.serialization.Serializable

/** `GET /healthz` のレスポンス */
@Serializable
data class HealthResponse(
    val status: String,
)
