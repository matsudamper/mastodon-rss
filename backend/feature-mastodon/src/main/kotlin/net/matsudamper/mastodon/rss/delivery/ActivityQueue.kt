package net.matsudamper.mastodon.rss.delivery

import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.entity.PublicNoteId

/**
 * 相手の inbox に送るものを待ち行列に投函する口。
 *
 * 実際に送るのは `:backend` の配信ワーカーで、送れなかったものは間を空けて送り直される。
 * ここから投函したものは、プロセスが落ちても次の起動で送り直される。
 *
 * [ActivityDelivery] と違い、投函した時点では相手が受け取ったかどうかは分からない。
 * 受け取れたことを前提に進む処理は書けない。
 */
interface ActivityQueue {
    /**
     * @param sender 署名するこちらのアクター
     * @param inboxes 宛先。同じ宛先は 1 つにまとめてから渡すこと。
     *   [ActivityDelivery.deliver] と同じく、相手のアクターと同じホストであることを
     *   確認済みのものだけを渡すこと
     * @param body 署名対象になる JSON。宛先ごとに同じものを送る
     * @param notePublicId 記録済みの投稿を配るなら、その投稿。投稿を消すと未配信の行も
     *   一緒に消える。投稿を伴わない種別では null
     * @return 投函した件数。投稿が既に消えていて何も投函しなかったなら 0
     */
    fun enqueue(
        kind: QueuedActivityKind,
        sender: ActorUrls,
        inboxes: List<String>,
        body: String,
        notePublicId: PublicNoteId?,
    ): Int
}

/**
 * 投函するものの種別。何を送る行かが分かれば、配信の失敗を種別ごとに追える
 */
enum class QueuedActivityKind {
    CREATE_NOTE,
    ACCEPT_FOLLOW,
    DELETE_NOTE,
    DELETE_ACTOR,
    UPDATE_ACTOR,
}
