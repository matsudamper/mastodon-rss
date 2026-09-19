package net.matsudamper.mastodon.rss.repository.sqlite

import java.time.Instant
import java.util.TreeMap
import net.matsudamper.mastodon.rss.repository.FollowerRepository
import net.matsudamper.mastodon.rss.repository.IncomingFollow
import net.matsudamper.mastodon.rss.repository.NewRemoteActor
import net.matsudamper.mastodon.rss.repository.jooq.Tables.FOLLOWERS
import net.matsudamper.mastodon.rss.repository.jooq.Tables.REMOTE_ACTORS
import net.matsudamper.mastodon.rss.repository.sqlite.db.DeliveryKindDbValue
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Select
import org.jooq.impl.DSL

internal class SqliteFollowerRepository(
    private val jooq: SqliteJooq,
) : FollowerRepository {
    /**
     * 3 テーブルへの書き込みを 1 トランザクションにまとめる。
     * 途中で落ちると、誰も指していない相手のアクターの行や、
     * 記録の無いフォローへの `Accept` が残る。
     */
    override fun record(follow: IncomingFollow) {
        jooq.transaction { dsl ->
            val remoteActorId = upsertRemoteActor(dsl, follow.follower, follow.receivedAt)

            dsl
                .insertInto(FOLLOWERS)
                .set(FOLLOWERS.USERNAME, follow.username)
                .set(FOLLOWERS.REMOTE_ACTOR_ID, remoteActorId)
                .set(FOLLOWERS.FOLLOW_ACTIVITY_URI, follow.followActivityUri)
                .set(FOLLOWERS.STATE, STATE_PENDING)
                .set(FOLLOWERS.CREATED_AT, StoredInstant.format(follow.receivedAt))
                .onConflict(FOLLOWERS.USERNAME, FOLLOWERS.REMOTE_ACTOR_ID)
                // 行は増やさないが、`Follow` の id だけは最後に受けたものに差し替える。
                // 相手は受理された `Follow` の id で `Undo` を送ってくるので、
                // 古い id を残すと解除の指定と食い違って消せなくなる。
                // 状態と作成時刻は触らない。触ると再送のたびにフォロワーが
                // `Accept` 前の状態に戻る
                .doUpdate()
                .set(FOLLOWERS.FOLLOW_ACTIVITY_URI, follow.followActivityUri)
                .execute()

            // 送り直された `Follow` の分だけ `Accept` が増えないよう、
            // 未送信のものは最後に受けた `Follow` への `Accept` で置き換える
            DeliveryQueueRows.deletePendingAccept(
                dsl = dsl,
                username = follow.username,
                followerActorUri = follow.follower.actorUri,
            )

            DeliveryQueueRows.insertPending(
                dsl = dsl,
                kind = DeliveryKindDbValue.ACCEPT_FOLLOW,
                username = follow.username,
                // `sharedInbox` には送らない。`Accept` は相手 1 人への応答で、
                // まとめて送ると同じサーバーの他の利用者にも届く
                inbox = follow.follower.inbox,
                body = follow.acceptBody,
                enqueuedAt = follow.receivedAt,
                notePublicId = null,
                targetActorUri = follow.follower.actorUri,
            )
        }
    }

    override fun remove(
        username: String,
        followerActorUri: String,
        followActivityUri: String?,
    ): Boolean = jooq.transaction { dsl ->
        val condition: Condition = FOLLOWERS.ID
            .`in`(FollowerRows.ids(username = username, followerActorUri = followerActorUri))
            .let { base ->
                if (followActivityUri == null) {
                    base
                } else {
                    base.and(FOLLOWERS.FOLLOW_ACTIVITY_URI.eq(followActivityUri))
                }
            }

        val removed = dsl.deleteFrom(FOLLOWERS).where(condition).execute() > 0

        // 解除された相手に `Accept` を送っても、相手にはもう対応するフォローが無い
        if (removed) {
            DeliveryQueueRows.deletePendingAccept(dsl = dsl, username = username, followerActorUri = followerActorUri)
        }

        removed
    }

    override fun removeAccount(username: String): Int = jooq.transaction { dsl ->
        // `remote_actors` は残す。同じ相手が他のアカウントもフォローしていることがあり、
        // ここで消すと外部キーでそちらのフォローまで消える
        dsl
            .deleteFrom(FOLLOWERS)
            .where(FOLLOWERS.USERNAME.eq(username))
            .execute()
    }

    /**
     * `followers` を先に消してから `remote_actors` を消す。
     *
     * 外部キーは `ON DELETE CASCADE` なので `remote_actors` だけ消してもフォローは
     * 一緒に消えるが、それだと何件消えたのかが分からない。
     */
    override fun removeRemoteActor(actorUri: String): Int = jooq.transaction { dsl ->
        val removed = dsl
            .deleteFrom(FOLLOWERS)
            .where(FOLLOWERS.REMOTE_ACTOR_ID.`in`(remoteActorId(actorUri)))
            .execute()

        dsl.deleteFrom(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri)).execute()

        removed
    }

    /**
     * フォローが 1 件も残っていない相手の鍵は返さない。
     *
     * `remote_actors` の行はフォローを消しても残る。他のアカウントをフォローしている
     * ことがあるため。返してしまうと、解除した相手の鍵で署名を通せる
     */
    override fun findPublicKeyPem(actorUri: String): String? = jooq.withConnection { dsl ->
        dsl
            .select(REMOTE_ACTORS.PUBLIC_KEY_PEM)
            .from(REMOTE_ACTORS)
            .join(FOLLOWERS)
            .on(FOLLOWERS.REMOTE_ACTOR_ID.eq(REMOTE_ACTORS.ID))
            .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
            .limit(1)
            .fetchOne(REMOTE_ACTORS.PUBLIC_KEY_PEM)
    }

    /**
     * 同じ鍵なら書かない。読むたびに書くと、変わっていない行の fetched_at だけが動く
     */
    override fun rememberPublicKeyPem(
        actorUri: String,
        publicKeyPem: String,
        readAt: Instant,
    ) {
        jooq.transaction { dsl ->
            dsl
                .update(REMOTE_ACTORS)
                .set(REMOTE_ACTORS.PUBLIC_KEY_PEM, publicKeyPem)
                .set(REMOTE_ACTORS.FETCHED_AT, StoredInstant.format(readAt))
                .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
                .and(REMOTE_ACTORS.PUBLIC_KEY_PEM.ne(publicKeyPem))
                .execute()
        }
    }

    override fun list(
        username: String,
        after: String?,
        limit: Int,
    ): List<String> = jooq.withConnection { dsl ->
        dsl
            .select(REMOTE_ACTORS.ACTOR_URI)
            .from(FOLLOWERS)
            .join(REMOTE_ACTORS)
            .on(REMOTE_ACTORS.ID.eq(FOLLOWERS.REMOTE_ACTOR_ID))
            .where(FOLLOWERS.USERNAME.eq(username))
            .and(FOLLOWERS.STATE.eq(STATE_ACCEPTED))
            .and(after?.let { REMOTE_ACTORS.ACTOR_URI.gt(it) } ?: DSL.noCondition())
            // URL 順。位置を指す鍵が返す値そのもので済む
            .orderBy(REMOTE_ACTORS.ACTOR_URI)
            .limit(limit)
            .fetch(REMOTE_ACTORS.ACTOR_URI)
    }

    override fun count(username: String): Long = jooq.withConnection { dsl ->
        dsl
            .selectCount()
            .from(FOLLOWERS)
            .where(FOLLOWERS.USERNAME.eq(username))
            .and(FOLLOWERS.STATE.eq(STATE_ACCEPTED))
            .fetchOne(0, Long::class.java)
            ?: 0L
    }

    /**
     * 列に COLLATE NOCASE が付いているので、返ってくる綴りは渡した名前と揃わない。
     * 呼び出し側が渡した綴りで引けるよう、綴りの揺れを無視して詰め替える。
     */
    override fun counts(usernames: Set<String>): Map<String, Long> {
        if (usernames.isEmpty()) return emptyMap()

        return jooq.withConnection { dsl ->
            val counted = TreeMap<String, Long>(String.CASE_INSENSITIVE_ORDER)

            dsl
                .select(FOLLOWERS.USERNAME, DSL.count())
                .from(FOLLOWERS)
                .where(FOLLOWERS.USERNAME.`in`(usernames))
                .and(FOLLOWERS.STATE.eq(STATE_ACCEPTED))
                .groupBy(FOLLOWERS.USERNAME)
                .fetch()
                .forEach { counted[it.value1()] = it.value2().toLong() }

            usernames.associateWith { counted[it] ?: 0L }
        }
    }

    override fun deliveryTargets(username: String): List<String> = jooq.withConnection { dsl ->
        dsl
            .selectDistinct(DSL.coalesce(REMOTE_ACTORS.SHARED_INBOX, REMOTE_ACTORS.INBOX))
            .from(FOLLOWERS)
            .join(REMOTE_ACTORS)
            .on(REMOTE_ACTORS.ID.eq(FOLLOWERS.REMOTE_ACTOR_ID))
            .where(FOLLOWERS.USERNAME.eq(username))
            .and(FOLLOWERS.STATE.eq(STATE_ACCEPTED))
            .fetch()
            .map { it.value1() }
    }

    override fun hasAny(): Boolean = jooq.withConnection { dsl -> dsl.fetchExists(DSL.selectOne().from(FOLLOWERS)) }

    /**
     * 相手のアクターは毎回上書きする。inbox も鍵も相手の都合で変わるので、
     * 取り直したものが最新になる。
     */
    private fun upsertRemoteActor(
        dsl: DSLContext,
        actor: NewRemoteActor,
        now: Instant,
    ): Long {
        val fetchedAt = StoredInstant.format(now)

        dsl
            .insertInto(REMOTE_ACTORS)
            .set(REMOTE_ACTORS.ACTOR_URI, actor.actorUri)
            .set(REMOTE_ACTORS.INBOX, actor.inbox)
            .set(REMOTE_ACTORS.SHARED_INBOX, actor.sharedInbox)
            .set(REMOTE_ACTORS.PUBLIC_KEY_PEM, actor.publicKeyPem)
            .set(REMOTE_ACTORS.FETCHED_AT, fetchedAt)
            .onConflict(REMOTE_ACTORS.ACTOR_URI)
            .doUpdate()
            .set(REMOTE_ACTORS.INBOX, actor.inbox)
            .set(REMOTE_ACTORS.SHARED_INBOX, actor.sharedInbox)
            .set(REMOTE_ACTORS.PUBLIC_KEY_PEM, actor.publicKeyPem)
            .set(REMOTE_ACTORS.FETCHED_AT, fetchedAt)
            .execute()

        return checkNotNull(
            dsl
                .select(REMOTE_ACTORS.ID)
                .from(REMOTE_ACTORS)
                .where(REMOTE_ACTORS.ACTOR_URI.eq(actor.actorUri))
                .fetchOne(REMOTE_ACTORS.ID),
        ) { "相手のアクターの行を作れなかった: ${actor.actorUri}" }
    }

    private fun remoteActorId(actorUri: String): Select<Record1<Long>> = DSL
        .select(REMOTE_ACTORS.ID)
        .from(REMOTE_ACTORS)
        .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))

    private companion object {
        const val STATE_PENDING = FollowerRows.STATE_PENDING

        const val STATE_ACCEPTED = FollowerRows.STATE_ACCEPTED
    }
}
