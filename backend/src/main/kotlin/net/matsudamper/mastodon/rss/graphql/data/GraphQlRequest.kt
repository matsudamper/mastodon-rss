package net.matsudamper.mastodon.rss.graphql.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * `variables` を [JsonObject] のまま受けるのは、型が問い合わせごとに変わるため
 */
@Serializable
data class GraphQlRequest(
    @SerialName("query")
    val query: String,
    @SerialName("operationName")
    val operationName: String? = null,
    @SerialName("variables")
    val variables: JsonObject? = null,
)
