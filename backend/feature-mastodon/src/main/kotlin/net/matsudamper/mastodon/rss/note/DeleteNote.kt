package net.matsudamper.mastodon.rss.note

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.matsudamper.mastodon.rss.activitypub.OutgoingActivity
import net.matsudamper.mastodon.rss.activitypub.StringListSerializer
import net.matsudamper.mastodon.rss.entity.ActivityPubId

/**
 * 投稿を消したことを伝える `Delete`。
 *
 * こちらで消しても相手のサーバーには残る。これを配らない限り、
 * フォロワーのタイムラインからは消えない。
 *
 * `to` と `cc` は元の投稿と同じものを入れる。宛先を見て配るかどうかを決める実装があり、
 * 元の投稿と揃っていないと消したことが無視されてタイムラインに残る。
 */
@Serializable
data class DeleteNote(
    @SerialName("@context")
    @Serializable(with = StringListSerializer::class)
    val context: List<String> = OutgoingActivity.DEFAULT_CONTEXT,
    /**
     * このアクティビティ自身の id。消す投稿の id ではない。
     *
     * 相手の重複判定に使われるので、消した投稿の id と同じにしてはいけない
     */
    val id: ActivityPubId,
    val actor: String,
    val to: List<String>,
    val cc: List<String>,
    @SerialName("object")
    val target: Tombstone,
) {
    /**
     * 相手はこの値を見て削除だと判断する。`Delete` 以外は入らない
     */
    val type: String = "Delete"
}
