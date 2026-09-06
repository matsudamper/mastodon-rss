package net.matsudamper.mastodon.rss.note

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 投稿に添える画像。
 *
 * Mastodon は添付が付いた投稿にリンクのプレビューカードを出さない。
 * 記事のリンク先の `og:image` を添付として送るのは、相手側にカードを
 * 作らせる代わりに、こちらが選んだ 1 枚を出すという選択になる。
 *
 * `mediaType` は入れない。こちらは画像の URL を知っているだけで、
 * 中身が何かは取りに行くまで分からない。Mastodon は取得した応答から
 * 種類を決めるので、当てずっぽうの値を入れるより無いほうがよい。
 */
@Serializable
data class NoteAttachment(
    @SerialName("url")
    val url: String,
) {
    @SerialName("type")
    val type: String = "Image"
}
