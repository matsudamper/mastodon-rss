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

    val ICO: ByteArray = byteArrayOf(0x00, 0x00, 0x01, 0x00)

    /**
     * 同じ種類のまま中身だけを変える。
     */
    fun jpegOf(content: String): ByteArray = JPEG + content.toByteArray()
}
