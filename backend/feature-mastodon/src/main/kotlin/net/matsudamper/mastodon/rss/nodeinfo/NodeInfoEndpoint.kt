package net.matsudamper.mastodon.rss.nodeinfo

import net.matsudamper.mastodon.rss.http.EndpointResponse
import net.matsudamper.mastodon.rss.http.MediaType

/**
 * NodeInfo (https://nodeinfo.diaspora.software/) のエンドポイント。
 *
 * ActivityPub の仕様には含まれないが、フェデレーション状況を集計する
 * クローラーや調査ツールがソフトウェアの種類・バージョンを調べるのに使う。
 * 無くても Mastodon からのフォローには影響しない任意実装。
 *
 * @param domain `/nodeinfo/2.1` の絶対 URL を組み立てるためだけに使う。
 *   [net.matsudamper.mastodon.rss.actor.ActorUrls] と違いここでは id 等を持たないので、
 *   専用の URL 組み立てクラスは作らずそのまま受け取る
 */
class NodeInfoEndpoint(
    private val domain: String,
) {
    /**
     * `/.well-known/nodeinfo`
     */
    fun discovery(): EndpointResponse =
        EndpointResponse.json(
            serializer = NodeInfoDiscovery.serializer(),
            value =
            NodeInfoDiscovery(
                links =
                listOf(
                    NodeInfoDiscoveryLink(
                        rel = NodeInfoDiscoveryLink.REL_2_1,
                        href = "https://$domain/nodeinfo/2.1",
                    ),
                ),
            ),
            contentType = MediaType.Json,
        )

    /**
     * `/nodeinfo/2.1`
     */
    fun nodeInfo(): EndpointResponse =
        EndpointResponse.json(
            serializer = NodeInfo.serializer(),
            value =
            NodeInfo(
                // version は build.gradle.kts の allprojects.version と合わせる。自動で追従はしない
                software =
                NodeInfoSoftware(
                    name = "mastodon-rss",
                    version = "0.1.0",
                    repository = "https://github.com/matsudamper/mastodon-rss",
                ),
                usage = NodeInfoUsage(users = NodeInfoUsers(total = 1)),
            ),
            contentType = MediaType.Json,
        )
}
