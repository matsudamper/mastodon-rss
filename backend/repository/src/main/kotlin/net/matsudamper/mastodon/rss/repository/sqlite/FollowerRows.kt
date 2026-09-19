package net.matsudamper.mastodon.rss.repository.sqlite

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.jooq.Tables.FOLLOWERS
import net.matsudamper.mastodon.rss.repository.jooq.Tables.REMOTE_ACTORS
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Select
import org.jooq.impl.DSL

/**
 * `followers` の絞り込みと状態の書き換え。
 *
 * フォローの記録と、`Accept` を送れたことの記録は別のリポジトリから書く。
 * 同じ絞り込みをそれぞれに持たせると、状態の名前や結合の仕方が片方だけずれる。
 */
internal object FollowerRows {
    /**
     * `Follow` は受けたが `Accept` を返せていない
     */
    const val STATE_PENDING = "pending"

    /**
     * `Accept` を返せた。ここまで来たものだけをフォロワーとして数える
     */
    const val STATE_ACCEPTED = "accepted"

    /**
     * 名前とアクター URL の組からフォローの行を引く副問い合わせ。
     * 更新と削除で同じ絞り込みを使う。
     */
    fun ids(
        username: String,
        followerActorUri: String,
    ): Select<Record1<Long>> = DSL
        .select(FOLLOWERS.ID)
        .from(FOLLOWERS)
        .join(REMOTE_ACTORS)
        .on(REMOTE_ACTORS.ID.eq(FOLLOWERS.REMOTE_ACTOR_ID))
        .where(FOLLOWERS.USERNAME.eq(username))
        .and(REMOTE_ACTORS.ACTOR_URI.eq(followerActorUri))

    /**
     * まだ成立していない行を成立させる。
     *
     * 件数で初回かどうかが分かるので、状態を読んでから書くより競合に強い。
     * どの `Follow` に対する `Accept` だったかは問わない。相手から見ると、送った
     * `Follow` のどれか 1 つに `Accept` が返れば関係は成立する。
     *
     * @return 初めて成立したなら true。送り直しの `Accept` と記録が無い場合は false
     */
    fun markAccepted(
        dsl: DSLContext,
        username: String,
        followerActorUri: String,
        acceptedAt: Instant,
    ): Boolean = dsl
        .update(FOLLOWERS)
        .set(FOLLOWERS.STATE, STATE_ACCEPTED)
        .set(FOLLOWERS.ACCEPTED_AT, StoredInstant.format(acceptedAt))
        .where(FOLLOWERS.ID.`in`(ids(username = username, followerActorUri = followerActorUri)))
        .and(FOLLOWERS.STATE.ne(STATE_ACCEPTED))
        .execute() > 0
}
