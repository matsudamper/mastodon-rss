package net.matsudamper.mastodon.rss

import kotlinx.serialization.Serializable

/** `GET /healthz` のレスポンス */
@Serializable
data class HealthResponse(
    val status: String,
)
