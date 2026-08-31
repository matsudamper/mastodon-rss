package net.matsudamper.mastodon.rss

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /healthz` のレスポンス */
@Serializable
data class HealthResponse(
    @SerialName("status")
    val status: String,
)
