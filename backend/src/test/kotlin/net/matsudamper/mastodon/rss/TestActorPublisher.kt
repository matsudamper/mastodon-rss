package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.actor.ActorPublisher
import net.matsudamper.mastodon.rss.logic.ActorEnqueuer
import net.matsudamper.mastodon.rss.logic.RepositoryActorProfiles
import net.matsudamper.mastodon.rss.logic.RepositoryFeedLinks

/**
 * 載せる中身を本物と同じ引き先から組み立てる [ActorPublisher]
 */
object TestActorPublisher {
    fun of(repositories: FakeRepositories): ActorPublisher = ActorPublisher(
        actorKey = TestActorKey.value,
        feedLinks = RepositoryFeedLinks(
            accounts = repositories.accounts,
            feeds = repositories.feeds,
            headers = repositories.feedHeaders,
        ),
        profiles = RepositoryActorProfiles(repositories.accounts),
        webPages = TestWebPageUrls,
    )

    /**
     * 上の [of] を配信キューに繋いだ [ActorEnqueuer]
     */
    fun enqueuerOf(repositories: FakeRepositories): ActorEnqueuer = ActorEnqueuer(
        publisher = of(repositories),
        followers = repositories.followers,
        deliveryQueue = repositories.deliveryQueue,
    )
}
