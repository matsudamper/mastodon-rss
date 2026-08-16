package net.matsudamper.mastodon.rss.graphql.data

import kotlinx.serialization.Serializable

@Serializable
internal data class GraphQlBadRequest(
    val message: String,
)
