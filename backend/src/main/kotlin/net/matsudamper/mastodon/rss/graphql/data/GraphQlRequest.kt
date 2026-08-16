package net.matsudamper.mastodon.rss.graphql.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * `variables` を [JsonObject] のまま受けるのは、型が問い合わせごとに変わるため
 */
@Serializable
data class GraphQlRequest(
    val query: String,
    val operationName: String? = null,
    val variables: JsonObject? = null,
)
