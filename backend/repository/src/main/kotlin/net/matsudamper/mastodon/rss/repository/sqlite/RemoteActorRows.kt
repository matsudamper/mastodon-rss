package net.matsudamper.mastodon.rss.repository.sqlite

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.NewRemoteActor
import net.matsudamper.mastodon.rss.repository.jooq.Tables.FOLLOWERS
import net.matsudamper.mastodon.rss.repository.jooq.Tables.NOTE_FAVOURITES
import net.matsudamper.mastodon.rss.repository.jooq.Tables.REMOTE_ACTORS
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Select
import org.jooq.impl.DSL

/**
 * `remote_actors` の書き込みと引き当て。
 *
 * フォローとお気に入りのどちらを受けても同じ行を作る。片方だけが別の書き方をすると、
 * 相手が消えた後に鍵を引けるかどうかが受け取った経路で変わる。
 */
internal object RemoteActorRows {
    /**
     * 相手のアクターは毎回上書きする。inbox も鍵も相手の都合で変わるので、
     * 取り直したものが最新になる。
     */
    fun upsert(
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
            .set(REMOTE_ACTORS.PREFERRED_USERNAME, actor.profile.preferredUsername)
            .set(REMOTE_ACTORS.DISPLAY_NAME, actor.profile.displayName)
            .set(REMOTE_ACTORS.PROFILE_URL, actor.profile.profileUrl)
            .set(REMOTE_ACTORS.ICON_URL, actor.profile.iconUrl)
            .onConflict(REMOTE_ACTORS.ACTOR_URI)
            .doUpdate()
            .set(REMOTE_ACTORS.INBOX, actor.inbox)
            .set(REMOTE_ACTORS.SHARED_INBOX, actor.sharedInbox)
            .set(REMOTE_ACTORS.PUBLIC_KEY_PEM, actor.publicKeyPem)
            .set(REMOTE_ACTORS.FETCHED_AT, fetchedAt)
            .set(REMOTE_ACTORS.PREFERRED_USERNAME, actor.profile.preferredUsername)
            .set(REMOTE_ACTORS.DISPLAY_NAME, actor.profile.displayName)
            .set(REMOTE_ACTORS.PROFILE_URL, actor.profile.profileUrl)
            .set(REMOTE_ACTORS.ICON_URL, actor.profile.iconUrl)
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
     * どこからも指されなくなった相手の行を消す。
     *
     * 行を指すテーブルを足したら、ここにも足す。漏らすと、まだ使っている行を消して
     * 外部キーで記録ごと消える
     */
    fun deleteIfUnreferenced(
        dsl: DSLContext,
        actorUri: String,
    ) {
        dsl
            .deleteFrom(REMOTE_ACTORS)
            .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
            .andNotExists(DSL.selectOne().from(FOLLOWERS).where(FOLLOWERS.REMOTE_ACTOR_ID.eq(REMOTE_ACTORS.ID)))
            .andNotExists(DSL.selectOne().from(NOTE_FAVOURITES).where(NOTE_FAVOURITES.REMOTE_ACTOR_ID.eq(REMOTE_ACTORS.ID)))
            .execute()
    }

    /**
     * アクター URL から行を引く副問い合わせ。
     */
    fun id(actorUri: String): Select<Record1<Long>> = DSL
        .select(REMOTE_ACTORS.ID)
        .from(REMOTE_ACTORS)
        .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
}
