package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import net.matsudamper.mastodon.rss.actor.RemoteActor
import net.matsudamper.mastodon.rss.follower.FollowAcceptResult
import net.matsudamper.mastodon.rss.follower.FollowerStore
import net.matsudamper.mastodon.rss.repository.FollowAcceptResult as RepositoryFollowAcceptResult
import net.matsudamper.mastodon.rss.repository.FollowerRepository
import net.matsudamper.mastodon.rss.repository.IncomingFollow
import net.matsudamper.mastodon.rss.repository.NewRemoteActor

/**
 * ActivityPub 側の [FollowerStore] を DB に繋ぐ。
 *
 * `:backend:feature-mastodon` が `:backend:repository` を知らないので型を持ち替える
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
                    profileUrl = follower.profileUrl,
                    acct = follower.acct,
                ),
                followActivityUri = followActivityUri,
                receivedAt = receivedAt,
            ),
        )
    }

    override fun markAccepted(
        username: String,
        followerActorUri: String,
        acceptedAt: Instant,
    ): FollowAcceptResult = when (
        followers.markAccepted(
            username = username,
            followerActorUri = followerActorUri,
            acceptedAt = acceptedAt,
        )
    ) {
        RepositoryFollowAcceptResult.FirstAccept -> FollowAcceptResult.FirstAccept
        RepositoryFollowAcceptResult.AlreadyAccepted -> FollowAcceptResult.AlreadyAccepted
        RepositoryFollowAcceptResult.NotFound -> FollowAcceptResult.NotFound
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

    override fun removeAccount(username: String): Int = followers.removeAccount(username)

    override fun removeRemoteActor(actorUri: String): Int = followers.removeRemoteActor(actorUri)

    override fun findPublicKeyPem(actorUri: String): String? = followers.findPublicKeyPem(actorUri)

    override fun rememberPublicKeyPem(
        actorUri: String,
        publicKeyPem: String,
    ) {
        followers.rememberPublicKeyPem(
            actorUri = actorUri,
            publicKeyPem = publicKeyPem,
            readAt = Instant.now(),
        )
    }

    /**
     * `followers` コレクションに並べるのはアクターの id だけなので、
     * 保存してあるプロフィールの URL や名前は落とす
     */
    override fun list(
        username: String,
        after: String?,
        limit: Int,
    ): List<String> = followers
        .list(username = username, after = after, limit = limit)
        .map { it.actorUri }

    override fun count(username: String): Long = followers.count(username)

    override fun deliveryTargets(username: String): List<String> = followers.deliveryTargets(username)
}
