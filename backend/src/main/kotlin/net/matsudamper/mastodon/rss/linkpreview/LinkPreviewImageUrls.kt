package net.matsudamper.mastodon.rss.linkpreview

import io.ktor.http.encodeURLParameter

/**
 * リンク先の OGP 画像を中継する URL。
 *
 * 配信元の URL を画面にそのまま出さないのは、画面が canvas に描くため。
 * 画像はブラウザの fetch で取ることになり、配信元が CORS を許していないと出ない。
 */
class LinkPreviewImageUrls(
    private val domain: String,
) {
    /**
     * @param imageUrl 取得元。ここから決まる値を付けるので、リンク先が画像を
     *   差し替えれば URL ごと変わる
     */
    fun image(
        pageUrl: String,
        imageUrl: String,
    ): String = "https://$domain$PATH?$PAGE_PARAMETER=${pageUrl.encodeURLParameter()}" +
        "&$VERSION_PARAMETER=${version(imageUrl)}"

    companion object {
        /**
         * 拡張子を付けてあるのは、キャッシュする対象を拡張子で決める CDN が
         * あるため。中身は画像だが種類は相手次第なので、種類を名乗らない
         * `.bin` にして、実際の種類は Content-Type で伝える
         */
        const val PATH: String = "/link-previews/image.bin"

        const val PAGE_PARAMETER: String = "page"

        const val VERSION_PARAMETER: String = "v"

        fun version(imageUrl: String): String = imageUrl.hashCode().toUInt().toString(HEX_RADIX)

        private const val HEX_RADIX = 16
    }
}
