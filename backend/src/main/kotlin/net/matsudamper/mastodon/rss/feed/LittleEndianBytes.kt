package net.matsudamper.mastodon.rss.feed

/**
 * ICO / BMP のヘッダーが使うリトルエンディアンの数値読み出し
 */
internal object LittleEndianBytes {
    fun u16(
        bytes: ByteArray,
        offset: Int,
    ): Int = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    /**
     * 32 ビットの値。オフセットや長さ、幅・高さは配信元の申告値で 2GB を超えうるので Long で扱う
     */
    fun u32(
        bytes: ByteArray,
        offset: Int,
    ): Long = u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
}
