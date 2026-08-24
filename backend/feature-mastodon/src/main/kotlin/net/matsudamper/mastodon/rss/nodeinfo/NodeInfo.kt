package net.matsudamper.mastodon.rss.nodeinfo

import kotlinx.serialization.Serializable

/**
 * `GET /.well-known/nodeinfo` のレスポンス。
 *
 * NodeInfo (https://nodeinfo.diaspora.software/) の入口。ドメインだけから
 * `/nodeinfo/2.1` の URL を機械的に組み立てられるが、仕様上は必ずこの discovery
 * document を経由させる決まりになっている。
 */
@Serializable
data class NodeInfoDiscovery(
    val links: List<NodeInfoDiscoveryLink>,
)

/**
 * @param rel NodeInfo のスキーマバージョンを表す固定 URI
 * @param href `/nodeinfo/2.1` の絶対 URL
 */
@Serializable
data class NodeInfoDiscoveryLink(
    val rel: String,
    val href: String,
) {
    companion object {
        const val REL_2_1: String = "http://nodeinfo.diaspora.software/ns/schema/2.1"
    }
}

/**
 * `GET /nodeinfo/2.1` のレスポンス本体。
 *
 * ローカルユーザー数はまだ数えていないので [NodeInfoUsage.users] は常に total = 1。
 * 記事の配信数もまだ数えていないので `localPosts` は常に 0。
 */
@Serializable
data class NodeInfo(
    val version: String = "2.1",
    val software: NodeInfoSoftware,
    val protocols: List<String> = listOf("activitypub"),
    val services: NodeInfoServices = NodeInfoServices(),
    val openRegistrations: Boolean = false,
    val usage: NodeInfoUsage,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * @param repository ソース公開先。NodeInfo 2.1 で software に追加された任意フィールド
 */
@Serializable
data class NodeInfoSoftware(
    val name: String,
    val version: String,
    val repository: String? = null,
)

@Serializable
data class NodeInfoServices(
    val inbound: List<String> = emptyList(),
    val outbound: List<String> = emptyList(),
)

@Serializable
data class NodeInfoUsage(
    val users: NodeInfoUsers,
    val localPosts: Int = 0,
)

@Serializable
data class NodeInfoUsers(
    val total: Int,
)
