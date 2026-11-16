package net.matsudamper.mastodon.rss

import net.matsudamper.kotpub.TestActorPrivateKey
import net.matsudamper.kotpub.TestDelivery
import net.matsudamper.kotpub.TestRemoteActors
import net.matsudamper.kotpub.actor.ActorPrivateKey
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.delivery.ActivityDelivery
import net.matsudamper.kotpub.http.ActivityPubClientResponse
import net.matsudamper.kotpub.http.ActivityPubHttpClient
import net.matsudamper.kotpub.http.ActivityPubPostResponse
import net.matsudamper.kotpub.http.ResponseBody
import net.matsudamper.mastodon.rss.feed.FeedFetchService
import net.matsudamper.mastodon.rss.repository.Repositories

/**
 * テストから [module] に渡す [AppDependencies]。
 *
 * 差し替えたいものだけを名前付きで指定して、残りはフェイクの既定に任せる。
 * ルーティングのテストが必要とするのは大抵 1 つか 2 つなので、
 * 全部を毎回並べると何を差し替えたのかが埋もれる。
 */
fun testDependencies(
    repositories: Repositories = FakeRepositories(),
    actorKey: ActorPrivateKey = TestActorPrivateKey.value,
    env: ServerEnv = TestServerEnv.value,
    remoteActors: RemoteActors = TestRemoteActors(),
    delivery: ActivityDelivery = TestDelivery(),
    feedFetcher: FeedFetchService = FeedFetchService(),
): AppDependencies =
    AppDependencies(
        repositories = repositories,
        actorKey = actorKey,
        env = env,
        remoteActors = remoteActors,
        remoteNotesClient = NotFoundActivityPubHttpClient,
        delivery = delivery,
        feedFetcher = feedFetcher,
    )

/**
 * 何を取りに行っても無いと答える。他のサーバーの投稿を取得しないテスト用
 */
private object NotFoundActivityPubHttpClient : ActivityPubHttpClient {
    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        maxBodyBytes: Int,
        readsBody: (Int, String) -> Boolean,
    ): ActivityPubClientResponse = ActivityPubClientResponse(status = 404, finalUrl = url, body = ResponseBody.NotRead)

    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: ByteArray,
    ): ActivityPubPostResponse = error("POST は使わない")

    override fun close() = Unit
}
