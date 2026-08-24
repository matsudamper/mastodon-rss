package net.matsudamper.mastodon.rss.repository.sqlite

import java.time.Instant
import java.util.TreeMap
import net.matsudamper.mastodon.rss.repository.FollowerRepository
import net.matsudamper.mastodon.rss.repository.IncomingFollow
import net.matsudamper.mastodon.rss.repository.NewRemoteActor
import net.matsudamper.mastodon.rss.repository.jooq.Tables.FOLLOWERS
import net.matsudamper.mastodon.rss.repository.jooq.Tables.REMOTE_ACTORS
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Select
import org.jooq.impl.DSL

internal class SqliteFollowerRepository(
    private val jooq: SqliteJooq,
) : FollowerRepository {
    /**
     * 2 テーブルへの書き込みを 1 トランザクションにまとめる。
     * 途中で落ちると、誰も指していない相手のアクターの行が残る。
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
        }
    }

    override fun markAccepted(
        username: String,
        followerActorUri: String,
        acceptedAt: Instant,
    ): Boolean = jooq.transaction { dsl ->
        dsl
            .update(FOLLOWERS)
            .set(FOLLOWERS.STATE, STATE_ACCEPTED)
            .set(FOLLOWERS.ACCEPTED_AT, StoredInstant.format(acceptedAt))
            .where(FOLLOWERS.ID.`in`(followerIds(username, followerActorUri)))
            .execute() > 0
    }

    override fun remove(
        username: String,
        followerActorUri: String,
        followActivityUri: String?,
    ): Boolean = jooq.transaction { dsl ->
        val condition: Condition = FOLLOWERS.ID
            .`in`(followerIds(username, followerActorUri))
            .let { base ->
                if (followActivityUri == null) {
                    base
                } else {
                    base.and(FOLLOWERS.FOLLOW_ACTIVITY_URI.eq(followActivityUri))
                }
            }

        dsl.deleteFrom(FOLLOWERS).where(condition).execute() > 0
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

    override fun list(
        username: String,
        after: String?,
        limit: Int,
    ): List<String> = jooq.transaction { dsl ->
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

    override fun count(username: String): Long = jooq.transaction { dsl ->
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

        return jooq.transaction { dsl ->
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

    override fun deliveryTargets(username: String): List<String> = jooq.transaction { dsl ->
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

    override fun hasAny(): Boolean = jooq.transaction { dsl -> dsl.fetchExists(DSL.selectOne().from(FOLLOWERS)) }

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

    /**
     * 名前とアクター URL の組からフォローの行を引く副問い合わせ。
     * 更新と削除で同じ絞り込みを使う。
     */
    private fun followerIds(
        username: String,
        followerActorUri: String,
    ): Select<Record1<Long>> = DSL
        .select(FOLLOWERS.ID)
        .from(FOLLOWERS)
        .join(REMOTE_ACTORS)
        .on(REMOTE_ACTORS.ID.eq(FOLLOWERS.REMOTE_ACTOR_ID))
        .where(FOLLOWERS.USERNAME.eq(username))
        .and(REMOTE_ACTORS.ACTOR_URI.eq(followerActorUri))

    private fun remoteActorId(actorUri: String): Select<Record1<Long>> = DSL
        .select(REMOTE_ACTORS.ID)
        .from(REMOTE_ACTORS)
        .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))

    private companion object {
        /**
         * `Follow` は受けたが `Accept` を返せていない
         */
        const val STATE_PENDING = "pending"

        /**
         * `Accept` を返せた。ここまで来たものだけをフォロワーとして数える
         */
        const val STATE_ACCEPTED = "accepted"
    }
}
