package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import net.matsudamper.mastodon.rss.actor.RemoteActor
import net.matsudamper.mastodon.rss.follower.FollowerStore
import net.matsudamper.mastodon.rss.repository.FollowerRepository
import net.matsudamper.mastodon.rss.repository.IncomingFollow
import net.matsudamper.mastodon.rss.repository.NewRemoteActor

/**
 * ActivityPub 側の [FollowerStore] を DB に繋ぐ。
 *
 * 型を持ち替えるだけの層があるのは、`:backend:feature-mastodon` が
 * `:backend:repository` を知らないため。同じ形の型を使い回すと、スキーマを変えるたびに
 * ActivityPub の実装を触ることになる。
 */
class RepositoryFollowerStore(
    private val followers: FollowerRepository,
) : FollowerStore {
    override fun record(
        username: String,
        follower: RemoteActor,
        followActivityUri: String,
        receivedAt: Instant,
    ) {
        followers.record(
            IncomingFollow(
                username = username,
                follower = NewRemoteActor(
                    actorUri = follower.actorId,
                    inbox = follower.inbox,
                    sharedInbox = follower.sharedInbox,
                    publicKeyPem = follower.publicKeyPem,
                ),
                followActivityUri = followActivityUri,
                receivedAt = receivedAt,
            ),
        )
    }

    override fun markAccepted(
        username: String,
        followerActorUri: String,
        followActivityUri: String,
        acceptedAt: Instant,
    ) {
        followers.markAccepted(
            username = username,
            followerActorUri = followerActorUri,
            followActivityUri = followActivityUri,
            acceptedAt = acceptedAt,
        )
    }

    override fun remove(
        username: String,
        followerActorUri: String,
        followActivityUri: String?,
    ): Boolean = followers.remove(
        username = username,
        followerActorUri = followerActorUri,
        followActivityUri = followActivityUri,
    )

    override fun removeRemoteActor(actorUri: String): Int = followers.removeRemoteActor(actorUri)

    override fun list(
        username: String,
        after: String?,
        limit: Int,
    ): List<String> = followers.list(username = username, after = after, limit = limit)

    override fun count(username: String): Long = followers.count(username)

    override fun deliveryTargets(username: String): List<String> = followers.deliveryTargets(username)
}
