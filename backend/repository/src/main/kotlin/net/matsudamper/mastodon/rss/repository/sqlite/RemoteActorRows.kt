package net.matsudamper.mastodon.rss.repository.sqlite

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.NewRemoteActor
import net.matsudamper.mastodon.rss.repository.jooq.Tables.REMOTE_ACTORS
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Select
import org.jooq.impl.DSL

internal object RemoteActorRows {
    /**
     * 相手のアクターは毎回上書きする。inbox も鍵も相手の都合で変わるので、
     * 取り直したものが最新になる。
     *
     * `Follow` を受け付けるときだけ使う。inbox は投稿の配る先になるので、
     * 配る先の上限を確かめた経路でしか置き換えない。
     */
    fun upsertFollower(
        dsl: DSLContext,
        actor: NewRemoteActor,
        now: Instant,
    ): Long = upsert(dsl = dsl, actor = actor, now = now, replacesDeliveryTargets = true)

    /**
     * 記録済みの相手の inbox と `sharedInbox` は置き換えない。
     *
     * フォロワーの行と共有しているので、ここで置き換えると `Follow` での上限の確認を通らずに
     * 投稿の配る先を増やせる。鍵とプロフィールは置き換える。
     */
    fun upsertKeepingDeliveryTargets(
        dsl: DSLContext,
        actor: NewRemoteActor,
        now: Instant,
    ): Long = upsert(dsl = dsl, actor = actor, now = now, replacesDeliveryTargets = false)

    private fun upsert(
        dsl: DSLContext,
        actor: NewRemoteActor,
        now: Instant,
        replacesDeliveryTargets: Boolean,
    ): Long {
        val fetchedAt = StoredInstant.format(now)
        val storedInbox = if (replacesDeliveryTargets) DSL.value(actor.inbox) else REMOTE_ACTORS.INBOX
        val storedSharedInbox = if (replacesDeliveryTargets) DSL.value(actor.sharedInbox) else REMOTE_ACTORS.SHARED_INBOX

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
            .set(REMOTE_ACTORS.INBOX, storedInbox)
            .set(REMOTE_ACTORS.SHARED_INBOX, storedSharedInbox)
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

    fun id(actorUri: String): Select<Record1<Long>> = DSL
        .select(REMOTE_ACTORS.ID)
        .from(REMOTE_ACTORS)
        .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
}
