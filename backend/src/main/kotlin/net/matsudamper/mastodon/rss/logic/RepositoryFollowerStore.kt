package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import net.matsudamper.kotpub.actor.RemoteActor
import net.matsudamper.kotpub.actor.RemoteActorProfile
import net.matsudamper.kotpub.follower.FollowerStore
import net.matsudamper.mastodon.rss.repository.FollowerRepository
import net.matsudamper.mastodon.rss.repository.IncomingFollow
import net.matsudamper.mastodon.rss.repository.RefusedFollow

/**
 * ActivityPub 側の [FollowerStore] を DB に繋ぐ。
 *
 * kotpub の `core` が `:backend:repository` を知らないので型を持ち替える
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

    /**
     * 同じホストのアクターからのフォローで配る先が上限に達していれば、新しいフォローを断る。
     *
     * `sharedInbox` を持たず inbox を 1 人ずつ分けた偽アクターを並べると、投稿 1 件ごとの
     * 配信がその人数分に増える。鍵付きのアクターはいくらでも作れるので、件数で止める。
     * アクター文書を置くホストは相手が用意する必要があるので、ホストごとに数える。
     *
     * 記録済みの相手は断らない。`Accept` が届かず送り直された `Follow` まで断ると、
     * 上限に達した後にフォローが消える。
     *
     * 受信を止めたドメインからの `Follow` は inbox で捨てているので、ここでは見ない。
     */
    override fun refuses(
        username: String,
        followerActorUri: String,
    ): Boolean {
        if (followers.hasFollow(username = username, followerActorUri = followerActorUri)) return false

        return followers.countDeliveryTargetsOfSameHost(
            username = username,
            followerActorUri = followerActorUri,
        ) >= MAX_DELIVERY_TARGETS_PER_ACTOR_HOST
    }

    override fun reject(
        username: String,
        follower: RemoteActor,
        followActivityUri: String,
        receivedAt: Instant,
        rejectBody: String,
    ): Boolean = followers.reject(
        RefusedFollow(
            username = username,
            followerActorUri = follower.actorId,
            inbox = follower.inbox,
            receivedAt = receivedAt,
            rejectBody = rejectBody,
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

    companion object {
        /**
         * Mastodon などの普通のインスタンスは `sharedInbox` で 1 か所にまとまるので、
         * ここに届くのは `sharedInbox` を持たない実装だけ
         */
        const val MAX_DELIVERY_TARGETS_PER_ACTOR_HOST = 20
    }
}
