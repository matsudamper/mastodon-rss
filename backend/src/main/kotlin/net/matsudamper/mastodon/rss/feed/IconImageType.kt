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

    /**
     * バイト列の先頭がこの種類のものか。
     *
     * 配信元は `image/png` と名乗って画像でないものを返せる。名乗りだけで通すと、
     * こちらのドメインから中身の分からないファイルを配ることになる
     */
    fun matches(bytes: ByteArray): Boolean = when (this) {
        PNG -> bytes.startsWith(PNG_SIGNATURE, 0)
        JPEG -> bytes.startsWith(JPEG_SIGNATURE, 0)
        GIF -> bytes.startsWith(GIF87A_SIGNATURE, 0) || bytes.startsWith(GIF89A_SIGNATURE, 0)
        // RIFF コンテナ。先頭 4 バイトの後ろにファイル長が入り、その次に形式が来る
        WEBP -> bytes.startsWith(RIFF_SIGNATURE, 0) && bytes.startsWith(WEBP_SIGNATURE, 8)
        ICO -> bytes.startsWith(ICO_SIGNATURE, 0)
    }

    companion object {
        /**
         * 配信元が名乗った Content-Type から種類を決める。扱えなければ null
         */
        fun of(contentType: ContentType): IconImageType? {
            val target = contentType.withoutParameters()
            return entries.find { type -> type.contentTypes.any { it == target } }
        }

        private fun ByteArray.startsWith(
            signature: ByteArray,
            offset: Int,
        ): Boolean {
            if (size < offset + signature.size) return false
            return signature.indices.all { index -> this[offset + index] == signature[index] }
        }

        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val GIF87A_SIGNATURE = "GIF87a".toByteArray()
        private val GIF89A_SIGNATURE = "GIF89a".toByteArray()
        private val RIFF_SIGNATURE = "RIFF".toByteArray()
        private val WEBP_SIGNATURE = "WEBP".toByteArray()
        private val ICO_SIGNATURE = byteArrayOf(0x00, 0x00, 0x01, 0x00)
    }
}
