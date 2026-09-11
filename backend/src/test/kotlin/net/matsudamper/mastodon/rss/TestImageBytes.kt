package net.matsudamper.mastodon.rss

/**
 * 取得の検査を通る最小のバイト列。
 *
 * 取得側は名乗った種類と中身の先頭が合っているかを見るので、
 * テストの応答にも本物と同じ先頭を持たせる。
 */
object TestImageBytes {
    val PNG: ByteArray = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    val JPEG: ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    val GIF: ByteArray = "GIF89a".toByteArray()

    /**
     * RIFF コンテナ。先頭の後ろにファイル長が入り、その次に形式が来る
     */
    val WEBP: ByteArray = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBP".toByteArray()

    /**
     * PNG の IEND チャンク。ICO から取り出す側は末尾がこれで終わっているかまで見るので、
     * 埋め込みの PNG は署名だけでなくこれも持たせる
     */
    val PNG_IEND: ByteArray = byteArrayOf(
        0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
    )

    /**
     * PNG を 1 枚だけ埋め込んだ ICO コンテナ。
     *
     * ICONDIR（6 バイト、reserved=0 / type=1 / count=1）+
     * ICONDIRENTRY（16 バイト、32x32 で埋め込みの PNG を指す）+
     * その PNG（[PNG] の署名 + [PNG_IEND]）。
     * ICO から埋め込み画像を取り出す側のテストで、実際に復号できる形として使う
     */
    val ICO: ByteArray = run {
        val embeddedPng = PNG + PNG_IEND
        val header = byteArrayOf(0, 0, 1, 0, 1, 0)
        val imageOffset = header.size + ENTRY_SIZE
        val entry = byteArrayOf(32, 32, 0, 0, 1, 0, 32, 0) + littleEndian(embeddedPng.size) + littleEndian(imageOffset)
        header + entry + embeddedPng
    }

    /**
     * 同じ種類のまま中身だけを変える。
     */
    fun jpegOf(content: String): ByteArray = JPEG + content.toByteArray()

    private const val ENTRY_SIZE = 16

    private fun littleEndian(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )
}
