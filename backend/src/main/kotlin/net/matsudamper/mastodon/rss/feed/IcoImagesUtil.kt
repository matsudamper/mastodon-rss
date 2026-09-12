package net.matsudamper.mastodon.rss.feed

/**
 * ICO コンテナに埋め込まれた画像を PNG として取り出す。
 *
 * favicon.ico の多くは複数解像度を 1 つの ICO コンテナに詰めている。ICO のまま
 * 配ると、Mastodon はこれをプロフィール画像として読めない（ブラウザで見えるのは
 * ブラウザ自身が ICO を解釈しているだけで、Mastodon 側の扱いとは別）。
 *
 * ICO の中の 1 枚が PNG ならバイト列としてそのまま完成した PNG なので、
 * コンテナから範囲を切り出すだけで配れる形になる。もう一方の形式である BMP（DIB）は
 * [DibDecoder] で展開して [PngEncoder] で PNG に描き直す
 */
object IcoImagesUtil {
    /**
     * 埋め込まれた画像のうち面積が一番大きく、かつ変換できるものを PNG として返す。
     *
     * 見つからない、または ICO として壊れているときは null
     */
    fun extractLargestImageAsPng(bytes: ByteArray): ByteArray? {
        val directory = IcoDirectory.parse(bytes) ?: return null
        return directory.entries
            .sortedByDescending { entry -> entry.area }
            .firstNotNullOfOrNull { entry -> imageAsPng(bytes, entry) }
    }

    /**
     * このエントリの中身を PNG バイト列にする。
     *
     * PNG 署名で始まり IEND チャンクで終わっていればそのまま切り出す。そうでなければ
     * BMP（DIB）として復号し、復号できなければ null（圧縮された DIB など）
     */
    private fun imageAsPng(
        bytes: ByteArray,
        entry: IcoDirectory.Entry,
    ): ByteArray? {
        if (isCompletePng(bytes, entry)) {
            return bytes.copyOfRange(entry.imageOffset, entry.imageOffset + entry.imageSize)
        }
        val decoded = DibDecoder.decode(bytes, entry) ?: return null
        return PngEncoder.encode(decoded.width, decoded.height, decoded.rgba)
    }

    /**
     * 範囲が PNG 署名で始まり IEND チャンクで終わっているか。
     *
     * ICONDIRENTRY の size は配信元の申告値で、実際の PNG より短く申告されていることがある。
     * 署名だけを見ると、途中で切れた PNG をそのまま image/png として配ってしまう
     */
    private fun isCompletePng(
        bytes: ByteArray,
        entry: IcoDirectory.Entry,
    ): Boolean {
        val start = entry.imageOffset
        val size = entry.imageSize
        if (size < PngEncoder.SIGNATURE.size + PngEncoder.IEND_CHUNK.size) return false
        if (!bytes.regionStartsWith(start, PngEncoder.SIGNATURE)) return false
        return bytes.regionStartsWith(start + size - PngEncoder.IEND_CHUNK.size, PngEncoder.IEND_CHUNK)
    }

    private fun ByteArray.regionStartsWith(
        offset: Int,
        signature: ByteArray,
    ): Boolean = signature.indices.all { index -> this[offset + index] == signature[index] }
}
