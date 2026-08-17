package net.matsudamper.mastodon.rss.note

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.matsudamper.mastodon.rss.activitypub.OutgoingActivity
import net.matsudamper.mastodon.rss.activitypub.StringListSerializer

/**
 * 配信する投稿。
 *
 * `cc` にフォロワーのコレクションを入れないと、配信してもホームタイムラインに
 * 並ばないことがある。
 *
 * `@context` は単体で返すときだけ入れる。[CreateNote] に包むときは外側が持つ。
 */
@Serializable
data class Note(
    @SerialName("@context")
    val context: List<String>? = null,
    val id: String,
    val type: String = TYPE,
    val attributedTo: String,
    val content: String,
    /**
     * ISO 8601
     */
    val published: String,
    val to: List<String>,
    val cc: List<String>,
    val url: String? = null,
) {
    companion object {
        const val TYPE: String = "Note"
    }
}

/**
 * [Note] を包んで配る `Create`。
 *
 * `to` と `cc` は中の [Note] と同じものを入れる。片方だけに入れると、
 * 実装によって配信先の判断が変わる。
 */
@Serializable
data class CreateNote(
    @SerialName("@context")
    @Serializable(with = StringListSerializer::class)
    val context: List<String> = OutgoingActivity.DEFAULT_CONTEXT,
    val id: String,
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

/**
 * 誰でも見られることを表す宛先。
 *
 * `to` に入れると公開投稿、`cc` に入れると未収載になる。
 */
const val PUBLIC_AUDIENCE: String = "https://www.w3.org/ns/activitystreams#Public"
