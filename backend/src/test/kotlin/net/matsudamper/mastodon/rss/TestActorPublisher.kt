package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.actor.ActorPublisher
import net.matsudamper.mastodon.rss.delivery.ActivityDelivery
import net.matsudamper.mastodon.rss.follower.FollowerStore
import net.matsudamper.mastodon.rss.logic.RepositoryActorProfiles
import net.matsudamper.mastodon.rss.logic.RepositoryFeedLinks
import net.matsudamper.mastodon.rss.note.NoteStore

/**
 * 配信先だけを差し替えた [ActorPublisher]。載せる中身は本物と同じ引き先から組み立てる。
 */
object TestActorPublisher {
    fun of(
        repositories: FakeRepositories,
        notes: NoteStore,
        followers: FollowerStore,
        delivery: ActivityDelivery,
    ): ActorPublisher = ActorPublisher(
        notes = notes,
        followers = followers,
        delivery = delivery,
        actorKey = TestActorKey.value,
        feedLinks = RepositoryFeedLinks(
            accounts = repositories.accounts,
            feeds = repositories.feeds,
            headers = repositories.feedHeaders,
        ),
        profiles = RepositoryActorProfiles(repositories.accounts),
        webPages = TestWebPageUrls,
    )
}
