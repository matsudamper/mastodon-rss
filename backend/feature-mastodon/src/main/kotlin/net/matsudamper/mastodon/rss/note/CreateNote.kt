package net.matsudamper.mastodon.rss.note

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.matsudamper.mastodon.rss.activitypub.OutgoingActivity
import net.matsudamper.mastodon.rss.activitypub.StringListSerializer
import net.matsudamper.mastodon.rss.entity.ActivityPubId

/**
 * [Note] を包んで配る `Create`。
 *
 * `to` と `cc` は中の [Note] と同じものを入れる。片方だけに入れると、
 * 実装によって配信先の判断が変わる。
 *
 * [OutgoingActivity] と分けているのは、あちらの `object` が
 * [net.matsudamper.mastodon.rss.activitypub.LinkOrObject] で、受け取ったものを
 * そのまま返す `Accept` のための形だから。こちらは中身をこちらが組み立てる。
 */
@Serializable
data class CreateNote(
    @SerialName("@context")
    @Serializable(with = StringListSerializer::class)
    val context: List<String> = OutgoingActivity.DEFAULT_CONTEXT,
    /**
     * このアクティビティ自身の id。相手側の重複判定に使われる
     */
    val id: ActivityPubId,
    val type: String = TYPE,
    val actor: String,
    val published: String,
    val to: List<String>,
    val cc: List<String>,
    @SerialName("object")
    val target: Note,
) {
    companion object {
        const val TYPE: String = "Create"
    }
}
