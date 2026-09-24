package net.matsudamper.mastodon.rss.linkpreview

import io.ktor.http.encodeURLParameter

/**
 * リンク先の OGP 画像を中継する URL。
 *
 * 取得元の URL を画面にそのまま出さないのは、画面は canvas に描くので画像を
 * ブラウザの fetch で取ることになり、取得元が CORS を許していないと出ないため。
 */
class LinkPreviewImageUrls(
    private val domain: String,
) {
    /**
     * @param linkIndex [LinkPreviewService.links] の中の位置
     * @param sourceUrl 取得元。ここから決まる値を付けるので、リンク先が画像を
     *   差し替えれば URL ごと変わる
     */
    fun image(
        notePublicId: String,
        linkIndex: Int,
        sourceUrl: String,
    ): String = "https://$domain$PATH?$NOTE_PARAMETER=${notePublicId.encodeURLParameter()}" +
        "&$LINK_PARAMETER=$linkIndex&$VERSION_PARAMETER=${version(sourceUrl)}"

    companion object {
        /**
         * 拡張子を付けてあるのは、キャッシュする対象を拡張子で決める CDN が
         * あるため。種類は相手次第なので `.bin` にして、実際の種類は Content-Type で伝える
         */
        const val PATH: String = "/link-previews/image.bin"

        const val NOTE_PARAMETER: String = "note"

        const val LINK_PARAMETER: String = "link"

        const val VERSION_PARAMETER: String = "v"

        fun version(sourceUrl: String): String = sourceUrl.hashCode().toUInt().toString(HEX_RADIX)

        private const val HEX_RADIX = 16
    }
}
