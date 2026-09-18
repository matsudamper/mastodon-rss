package net.matsudamper.mastodon.rss.note

import java.time.Instant
import net.matsudamper.mastodon.rss.activity.CreateNoteActivity
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.delivery.ActivityQueue
import net.matsudamper.mastodon.rss.delivery.QueuedActivityKind
import net.matsudamper.mastodon.rss.entity.PublicNoteId
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.url.WebPageUrls
import org.slf4j.LoggerFactory

/**
 * フォローが成立した相手にだけ、フォローより前の投稿を配る。
 *
 * Mastodon も Misskey も、フォローを受け付けたときに過去の `Create` を送り直さない。
 * 相手が `outbox` を遡って取り込むことも保証されていないので、何もしないと
 * フォロー直後のタイムラインは空になり、次の新着が出るまで何も見えない。
 *
 * 送るのは保存済みの投稿から組み立て直した元と同じ `Create{Note}` で、
 * `Create.id` と `Note.id` と `published` は初回配信と一致する。相手はこの id で
 * 重複を判断するので、既に持っている投稿を二重に並べずに済む。
 *
 * 古い順に投函する。相手は受け取り順ではなく `published` で並べるが、
 * 同じホスト宛は投函した順に 1 件ずつ送られるので、途中で止まったときに
 * 新しい方から欠けるより古い方から欠ける方が読める形になる。
 *
 * 投函は `Accept` の後に行う。先に届いた `Create` は、フォローが成立していない相手からの
 * ものとして相手のタイムラインに入らない。
 */
class FollowBackfillPublisher(
    private val notes: NoteStore,
    private val queue: ActivityQueue,
    private val webPages: WebPageUrls?,
) {
    private val logger = LoggerFactory.getLogger(FollowBackfillPublisher::class.java)

    /**
     * @param inbox 新しいフォロワーの inbox。`sharedInbox` は使わない。
     *   まとめて送ると、同じサーバーの他のフォロワーにも同じ投稿が届く
     * @param publishedBefore フォローが成立した時刻。これ以降の投稿は通常の配信で
     *   届くので送らない。含めると、その分だけフォロー前の投稿が上限から押し出される
     */
    fun enqueueRecentNotes(
        sender: ActorUrls,
        inbox: String,
        publishedBefore: Instant,
    ) {
        // 上限で切ってから絞ると、成立後に作られた投稿の分だけ送る件数が減る。
        // 一覧の位置として渡して、絞ってから 5 件を取る。id は空にする。
        // 同じ時刻の投稿は id の降順に並ぶので、空の id より後ろには何も無い
        val recentNotes = notes.list(
            username = sender.username,
            after = NotePosition(publishedAt = publishedBefore, publicId = PublicNoteId("")),
            limit = BACKFILL_LIMIT,
        )
        if (recentNotes.isEmpty()) return

        var queued = 0
        recentNotes.reversed().forEach { note ->
            val body = AppJson.encodeToString(
                CreateNoteActivity.serializer(),
                CreateNoteActivityFactory.create(sender = sender, note = note, webPages = webPages),
            )

            // 投函した行を投稿に繋ぐ。投函から送るまでの間に管理画面から消されても、
            // 行ごと消えて送られない。送ると、相手には `Delete` の後から `Create` が届く
            queued += queue.enqueue(
                kind = QueuedActivityKind.CREATE_NOTE,
                sender = sender,
                inboxes = listOf(inbox),
                body = body,
                notePublicId = note.publicId,
            )
        }

        logger.info("フォローされたので過去の投稿を投函した: ${sender.acct} → $inbox 投函=$queued/${recentNotes.size}")
    }

    private companion object {
        /**
         * 送る件数の上限。
         *
         * 全件送ると、記事の多いフィードでは 1 回のフォローで数百通が同じ相手に飛び、
         * 相手のインスタンスから見ると連投と変わらない。フォロー直後に
         * タイムラインが埋まる程度の件数にする
         */
        const val BACKFILL_LIMIT: Int = 5
    }
}
