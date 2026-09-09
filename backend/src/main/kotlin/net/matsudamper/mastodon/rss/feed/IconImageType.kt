package net.matsudamper.mastodon.rss.feed

import io.ktor.http.ContentType

/**
 * アイコンとして扱える画像の種類。
 *
 * 受け付ける Content-Type と、置くときの拡張子と、配るときの Content-Type を
 * 1 つにまとめる。受け付ける側と拡張子を別々に持つと、受け付ける側だけ増やしたときに
 * 取れるのに置けない種類ができる。
 *
 * ここに無い種類は受け付けない。とくに image/svg+xml はスクリプトを実行できるので、
 * 配信元の書いたものをこちらのドメインから配ると同一オリジンで動いてしまう
 */
enum class IconImageType(
    val fileExtension: String,
    /**
     * 受け付ける Content-Type。先頭を配るときに使う。残りは同じ種類を指す別名
     */
    private val contentTypes: List<ContentType>,
) {
    PNG("png", listOf(ContentType.Image.PNG)),
    JPEG("jpg", listOf(ContentType.Image.JPEG)),
    GIF("gif", listOf(ContentType.Image.GIF)),
    WEBP("webp", listOf(ContentType("image", "webp"))),

    /**
     * favicon はほとんどが ICO で、これを外すとサイトの favicon を充てても取れない。
     * 中身は画像だけでスクリプトを持たないので、こちらから配っても SVG のような問題は無い
     */
    ICO(
        "ico",
        listOf(
            ContentType("image", "x-icon"),
            ContentType("image", "vnd.microsoft.icon"),
        ),
    ),
    ;

    val contentType: ContentType get() = contentTypes.first()

    companion object {
        /**
         * 配信元が名乗った Content-Type から種類を決める。扱えなければ null
         */
        fun of(contentType: ContentType): IconImageType? {
            val target = contentType.withoutParameters()
            return entries.find { type -> type.contentTypes.any { it == target } }
        }
    }
}
