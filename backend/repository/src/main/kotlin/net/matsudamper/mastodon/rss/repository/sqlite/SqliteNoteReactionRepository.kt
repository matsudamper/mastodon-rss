package net.matsudamper.mastodon.rss.repository.sqlite

import net.matsudamper.mastodon.rss.repository.NewNoteReaction
import net.matsudamper.mastodon.rss.repository.NoteReactionCount
import net.matsudamper.mastodon.rss.repository.NoteReactionRepository
import net.matsudamper.mastodon.rss.repository.jooq.Tables.NOTES
import net.matsudamper.mastodon.rss.repository.jooq.Tables.NOTE_REACTIONS
import net.matsudamper.mastodon.rss.repository.jooq.Tables.REMOTE_ACTORS
import net.matsudamper.mastodon.rss.shared.PublicNoteId
import org.jooq.impl.DSL

internal class SqliteNoteReactionRepository(
    private val jooq: SqliteJooq,
) : NoteReactionRepository {
    override fun add(reaction: NewNoteReaction): Boolean = jooq.transaction { dsl ->
        // 配信していない投稿への反応は外部キーで弾かれる。例外で落とすと
        // inbox の処理の失敗として記録されるだけなので、先に見て false で返す
        val noteExists = dsl.fetchExists(
            DSL
                .selectOne()
                .from(NOTES)
                .where(NOTES.PUBLIC_ID.eq(reaction.notePublicId.value)),
        )
        if (noteExists.not()) return@transaction false

        // 数えるのと入れるのを同じトランザクションに入れる。分けると、反応が並んだときに
        // 上限を越えた分まで入る
        val storedInNote = dsl
            .selectCount()
            .from(NOTE_REACTIONS)
            .where(NOTE_REACTIONS.NOTE_PUBLIC_ID.eq(reaction.notePublicId.value))
            .fetchOne(0, Int::class.java) ?: 0
        if (storedInNote >= MAX_REACTIONS_PER_NOTE) return@transaction false

        // 相手の行は反応より先に作る。押した相手を鍵ごと残すのが目的なので、
        // 反応だけが入って相手の行が無い状態を作らない
        val remoteActorId = RemoteActorRows.upsert(dsl = dsl, actor = reaction.actor, now = reaction.receivedAt)

        val storedByActor = dsl
            .selectCount()
            .from(NOTE_REACTIONS)
            .where(NOTE_REACTIONS.NOTE_PUBLIC_ID.eq(reaction.notePublicId.value))
            .and(NOTE_REACTIONS.REMOTE_ACTOR_ID.eq(remoteActorId))
            .fetchOne(0, Int::class.java) ?: 0
        if (storedByActor >= MAX_REACTIONS_PER_ACTOR) return@transaction false

        val inserted = dsl
            .insertInto(NOTE_REACTIONS)
            .set(NOTE_REACTIONS.NOTE_PUBLIC_ID, reaction.notePublicId.value)
            .set(NOTE_REACTIONS.REMOTE_ACTOR_ID, remoteActorId)
            .set(NOTE_REACTIONS.ACTIVITY_URI, reaction.activityUri)
            .set(NOTE_REACTIONS.EMOJI, reaction.emoji)
            .set(NOTE_REACTIONS.EMOJI_IMAGE_URL, reaction.emojiImageUrl)
            .set(NOTE_REACTIONS.CREATED_AT, StoredInstant.format(reaction.receivedAt))
            .onConflictDoNothing()
            .execute()

        inserted > 0
    }

    override fun removeByActivityUri(
        actorUri: String,
        activityUri: String,
    ): Boolean = jooq.transaction { dsl ->
        dsl
            .deleteFrom(NOTE_REACTIONS)
            .where(NOTE_REACTIONS.ACTIVITY_URI.eq(activityUri))
            .and(NOTE_REACTIONS.REMOTE_ACTOR_ID.`in`(RemoteActorRows.id(actorUri)))
            .execute() > 0
    }

    override fun removeByEmoji(
        notePublicId: PublicNoteId,
        actorUri: String,
        emoji: String,
    ): Boolean = jooq.transaction { dsl ->
        dsl
            .deleteFrom(NOTE_REACTIONS)
            .where(NOTE_REACTIONS.NOTE_PUBLIC_ID.eq(notePublicId.value))
            .and(NOTE_REACTIONS.REMOTE_ACTOR_ID.`in`(RemoteActorRows.id(actorUri)))
            .and(NOTE_REACTIONS.EMOJI.eq(emoji))
            .execute() > 0
    }

    override fun removeByActor(actorUri: String): Int = jooq.transaction { dsl ->
        dsl
            .deleteFrom(NOTE_REACTIONS)
            .where(NOTE_REACTIONS.REMOTE_ACTOR_ID.`in`(RemoteActorRows.id(actorUri)))
            .execute()
    }

    /**
     * 反応が 1 件も残っていない相手の鍵は返さない。
     *
     * `remote_actors` の行は反応を消しても残る。フォロワーの鍵と同じく、
     * 返してしまうと関わりの切れた相手の鍵で署名を通せる
     */
    override fun findPublicKeyPem(actorUri: String): String? = jooq.withConnection { dsl ->
        dsl
            .select(REMOTE_ACTORS.PUBLIC_KEY_PEM)
            .from(REMOTE_ACTORS)
            .join(NOTE_REACTIONS)
            .on(NOTE_REACTIONS.REMOTE_ACTOR_ID.eq(REMOTE_ACTORS.ID))
            .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
            .limit(1)
            .fetchOne(REMOTE_ACTORS.PUBLIC_KEY_PEM)
    }

    override fun countsByNotes(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, List<NoteReactionCount>> {
        if (notePublicIds.isEmpty()) return mapOf()

        return jooq.withConnection { dsl ->
            val count = DSL.count()

            dsl
                .select(
                    NOTE_REACTIONS.NOTE_PUBLIC_ID,
                    NOTE_REACTIONS.EMOJI,
                    DSL.max(NOTE_REACTIONS.EMOJI_IMAGE_URL),
                    count,
                )
                .from(NOTE_REACTIONS)
                .where(NOTE_REACTIONS.NOTE_PUBLIC_ID.`in`(notePublicIds.map { it.value }))
                .groupBy(NOTE_REACTIONS.NOTE_PUBLIC_ID, NOTE_REACTIONS.EMOJI)
                // 同じ数なら綴りで並ぶ。決めておかないと、読み直すたびに並びが入れ替わる
                .orderBy(count.desc(), NOTE_REACTIONS.EMOJI.asc())
                .fetch()
                .groupBy(
                    { PublicNoteId(it.value1()) },
                    {
                        NoteReactionCount(
                            emoji = it.value2(),
                            emojiImageUrl = it.value3(),
                            count = it.value4(),
                        )
                    },
                )
        }
    }

    private companion object {
        /**
         * 1 つの投稿に、1 人の相手が持てる反応の数。
         *
         * 絵文字が違えば一意制約に当たらないので、content を変えるだけで
         * 1 人が何行でも積める。押せる絵文字は Misskey でも 1 投稿に 1 つで、
         * お気に入りと合わせてもこの数に届かない
         */
        const val MAX_REACTIONS_PER_ACTOR = 8

        /**
         * 1 つの投稿が持てる反応の数。
         *
         * 相手はアクターをいくつでも作れるので、相手ごとの上限だけでは
         * 人数ぶんだけ行が増える。フィードを流すだけのアカウントの投稿に
         * この数の反応が付くことは無く、届いた分はここまで数える
         */
        const val MAX_REACTIONS_PER_NOTE = 500
    }
}
