package net.matsudamper.mastodon.rss

import java.time.Instant
import net.matsudamper.mastodon.rss.actor.RemoteActor
import net.matsudamper.mastodon.rss.follower.FollowerStore

/**
 * フォロワーの記録の差し替え。オンメモリで持つ。
 *
 * SQL の振る舞いは `:backend:repository` のテストが本物の SQLite で確かめる。
 * こちらが受け持つのは、inbox のハンドラや配信が何をどの順で呼んだかの確認。
 *
 * @param failOnRecord 記録に失敗する状況を作る
 */
class FakeFollowerStore(
    private val failOnRecord: Boolean = false,
) : FollowerStore {
    val rows: MutableList<Row> = mutableListOf()

    override fun record(
        username: String,
        follower: RemoteActor,
        followActivityUri: String,
        receivedAt: Instant,
    ) {
        if (failOnRecord) throw IllegalStateException("記録に失敗した想定")

        // 一意制約と同じ判定。同じ相手からの Follow が既にあれば触らない
        if (rows.any { it.username == username && it.followerActorUri == follower.actorId }) return

        rows += Row(
            username = username,
            followerActorUri = follower.actorId,
            inbox = follower.inbox,
            sharedInbox = follower.sharedInbox,
            publicKeyPem = follower.publicKeyPem,
            followActivityUri = followActivityUri,
            accepted = false,
        )
    }

    override fun markAccepted(
        username: String,
        followerActorUri: String,
        acceptedAt: Instant,
    ) {
        val index = rows.indexOfFirst { it.username == username && it.followerActorUri == followerActorUri }
        if (index < 0) return
        rows[index] = rows[index].copy(accepted = true)
    }

    override fun remove(
        username: String,
        followerActorUri: String,
        followActivityUri: String?,
    ): Boolean = rows.removeAll { row ->
        row.username == username &&
            row.followerActorUri == followerActorUri &&
            (followActivityUri == null || row.followActivityUri == followActivityUri)
    }

    override fun removeRemoteActor(actorUri: String): Int {
        val before = rows.size
        rows.removeAll { it.followerActorUri == actorUri }
        return before - rows.size
    }

    override fun list(
        username: String,
        after: String?,
        limit: Int,
    ): List<String> = rows
        .filter { it.username == username && it.accepted }
        .map { it.followerActorUri }
        .sorted()
        .filter { after == null || it > after }
        .take(limit)

    override fun count(username: String): Long = rows.count { it.username == username && it.accepted }.toLong()

    override fun deliveryTargets(username: String): List<String> = rows
        .filter { it.username == username && it.accepted }
        .map { it.sharedInbox ?: it.inbox }
        .distinct()

    data class Row(
        val username: String,
        val followerActorUri: String,
        val inbox: String,
        val sharedInbox: String?,
        val publicKeyPem: String,
        val followActivityUri: String,
        val accepted: Boolean,
    )
}
