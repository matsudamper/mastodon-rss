package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import net.matsudamper.mastodon.rss.actor.RemoteActor
import net.matsudamper.mastodon.rss.actor.RemoteActorProfile
import net.matsudamper.mastodon.rss.follower.FollowerStore
import net.matsudamper.mastodon.rss.repository.FollowerRepository
import net.matsudamper.mastodon.rss.repository.IncomingFollow

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
        acceptBody: String,
    ): Boolean =
        followers.record(
            IncomingFollow(
                username = username,
                follower = StoredRemoteActors.of(follower),
                followActivityUri = followActivityUri,
                receivedAt = receivedAt,
                acceptBody = acceptBody,
            ),
        )

    override fun remove(
        username: String,
        followerActorUri: String,
        followActivityUri: String?,
    ): Boolean = followers.remove(
        username = username,
        followerActorUri = followerActorUri,
        followActivityUri = followActivityUri,
    )

    override fun findFolloweeUsername(
        followerActorUri: String,
        followActivityUri: String,
    ): String? = followers.findFolloweeUsername(followerActorUri = followerActorUri, followActivityUri = followActivityUri)

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

    override fun rememberProfile(
        actorUri: String,
        profile: RemoteActorProfile,
    ) {
        followers.rememberProfile(actorUri = actorUri, profile = StoredRemoteActors.of(profile))
    }

    /**
     * ActivityPub の `followers` コレクションはアクター URL しか出さないので、
     * 一緒に引けるプロフィールは落とす
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
