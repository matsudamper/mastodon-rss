package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.actor.ActorKey
import net.matsudamper.mastodon.rss.actor.RemoteActors
import net.matsudamper.mastodon.rss.delivery.ActivityDelivery
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
    actorKey: ActorKey = TestActorKey.value,
    env: ServerEnv = TestServerEnv.value,
    remoteActors: RemoteActors = TestRemoteActors(),
    delivery: ActivityDelivery = TestDelivery(),
): AppDependencies =
    AppDependencies(
        repositories = repositories,
        actorKey = actorKey,
        env = env,
        remoteActors = remoteActors,
        delivery = delivery,
    )
