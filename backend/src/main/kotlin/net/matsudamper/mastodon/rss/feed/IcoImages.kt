package net.matsudamper.mastodon.rss.feed

/**
 * ICO コンテナに埋め込まれた画像を取り出す。
 *
 * favicon.ico の多くは複数解像度を 1 つの ICO コンテナに詰めている。ICO のまま
 * 配ると、Mastodon はこれをプロフィール画像として読めない（ブラウザで見えるのは
 * ブラウザ自身が ICO を解釈しているだけで、Mastodon 側の扱いとは別）。
 *
 * ICO の中の 1 枚が PNG ならバイト列としてそのまま完成した PNG なので、
 * コンテナから範囲を切り出すだけで配れる形になる。中身が BMP（DIB、AND マスク付き）
 * のエントリしか無い ICO は、正しく再現するには別の復号処理が要るため対応しない。
 */
object IcoImages {
    /**
     * 埋め込まれた PNG のうち面積が一番大きいものを返す。
     *
     * 見つからない、または ICO として壊れているときは null。
     * 配信元が申告する位置・長さはそのまま信じず、ファイルの範囲に収まっているかを見る
     */
    fun extractLargestPng(bytes: ByteArray): ByteArray? {
        if (bytes.size < HEADER_SIZE) return null
        if (readU16(bytes, RESERVED_OFFSET) != 0) return null
        if (readU16(bytes, TYPE_OFFSET) != TYPE_ICON) return null

        val count = readU16(bytes, COUNT_OFFSET)
        var bestRange: IntRange? = null
        var bestArea = -1

        for (index in 0 until count) {
            val entry = HEADER_SIZE + index * ENTRY_SIZE
            if (entry + ENTRY_SIZE > bytes.size) break

            val range = pngRangeOf(bytes, entry) ?: continue
            val area = areaOf(bytes, entry)
            if (area <= bestArea) continue

            bestArea = area
            bestRange = range
        }

        return bestRange?.let { bytes.copyOfRange(it.first, it.last + 1) }
    }

    /**
     * ICONDIRENTRY が申告する幅と高さの面積。0 は 256 を表す
     */
    private fun areaOf(
        bytes: ByteArray,
        entry: Int,
    ): Int {
        val width = bytes[entry].toInt() and 0xFF
        val height = bytes[entry + 1].toInt() and 0xFF
        return (if (width == 0) SIDE_WHEN_ZERO else width) * (if (height == 0) SIDE_WHEN_ZERO else height)
    }

    /**
     * このエントリが指す画像データが PNG 署名で始まっていれば、その範囲を返す
     */
    private fun pngRangeOf(
        bytes: ByteArray,
        entry: Int,
    ): IntRange? {
        val size = readU32(bytes, entry + SIZE_OFFSET)
        val offset = readU32(bytes, entry + IMAGE_OFFSET_OFFSET)
        if (size < PNG_SIGNATURE.size) return null

        val end = offset + size
        if (end > bytes.size.toLong()) return null

        val start = offset.toInt()
        if (!bytes.regionStartsWith(start, PNG_SIGNATURE)) return null

        return start until end.toInt()
    }

    private fun ByteArray.regionStartsWith(
        offset: Int,
        signature: ByteArray,
    ): Boolean = signature.indices.all { index -> this[offset + index] == signature[index] }

    private fun readU16(
        bytes: ByteArray,
        offset: Int,
    ): Int = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    /**
     * 32 ビットの値。オフセットや長さは配信元の申告値で 2GB を超えうるので Long で扱う
     */
    private fun readU32(
        bytes: ByteArray,
        offset: Int,
    ): Long = readU16(bytes, offset).toLong() or (readU16(bytes, offset + 2).toLong() shl 16)

    /** ICONDIR の大きさ（reserved + type + count） */
    private const val HEADER_SIZE = 6

    /** ICONDIRENTRY 1 件の大きさ */
    private const val ENTRY_SIZE = 16
    private const val RESERVED_OFFSET = 0
    private const val TYPE_OFFSET = 2
    private const val COUNT_OFFSET = 4
    private const val SIZE_OFFSET = 8
    private const val IMAGE_OFFSET_OFFSET = 12

    /** ICONDIR.type がアイコン（カーソルではない）を表す値 */
    private const val TYPE_ICON = 1
    private const val SIDE_WHEN_ZERO = 256
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
}
