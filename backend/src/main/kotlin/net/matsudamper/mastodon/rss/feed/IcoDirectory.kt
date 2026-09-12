package net.matsudamper.mastodon.rss.feed

/**
 * ICO コンテナの目次（ICONDIR + ICONDIRENTRY の並び）。
 *
 * 配信元が申告する位置・長さはそのまま信じず、ファイルの範囲に収まるエントリだけを持つ
 */
internal class IcoDirectory private constructor(
    val entries: List<Entry>,
) {
    /**
     * ICONDIRENTRY 1 件。幅・高さは ICONDIRENTRY の申告値（0 は 256）
     */
    class Entry(
        val width: Int,
        val height: Int,
        val imageOffset: Int,
        val imageSize: Int,
    ) {
        val area: Int get() = width * height
    }

    companion object {
        fun parse(bytes: ByteArray): IcoDirectory? {
            if (bytes.size < HEADER_SIZE) return null
            if (LittleEndianBytes.u16(bytes, RESERVED_OFFSET) != 0) return null
            if (LittleEndianBytes.u16(bytes, TYPE_OFFSET) != TYPE_ICON) return null

            val count = LittleEndianBytes.u16(bytes, COUNT_OFFSET)
            val entries = (0 until count)
                .map { index -> HEADER_SIZE + index * ENTRY_SIZE }
                .takeWhile { entryOffset -> entryOffset + ENTRY_SIZE <= bytes.size }
                .mapNotNull { entryOffset -> entryAt(bytes, entryOffset) }
            return IcoDirectory(entries)
        }

        private fun entryAt(
            bytes: ByteArray,
            entryOffset: Int,
        ): Entry? {
            val size = LittleEndianBytes.u32(bytes, entryOffset + SIZE_OFFSET)
            val offset = LittleEndianBytes.u32(bytes, entryOffset + IMAGE_OFFSET_OFFSET)
            if (size <= 0 || offset + size > bytes.size.toLong()) return null

            return Entry(
                width = declaredSide(bytes[entryOffset + WIDTH_OFFSET]),
                height = declaredSide(bytes[entryOffset + HEIGHT_OFFSET]),
                imageOffset = offset.toInt(),
                imageSize = size.toInt(),
            )
        }

        private fun declaredSide(value: Byte): Int {
            val raw = value.toInt() and 0xFF
            return if (raw == 0) SIDE_WHEN_ZERO else raw
        }

        /**
         * ICONDIR の大きさ（reserved + type + count）
         */
        private const val HEADER_SIZE = 6

        /**
         * ICONDIRENTRY 1 件の大きさ
         */
        private const val ENTRY_SIZE = 16
        private const val RESERVED_OFFSET = 0
        private const val TYPE_OFFSET = 2
        private const val COUNT_OFFSET = 4
        private const val WIDTH_OFFSET = 0
        private const val HEIGHT_OFFSET = 1
        private const val SIZE_OFFSET = 8
        private const val IMAGE_OFFSET_OFFSET = 12

        /**
         * ICONDIR.type がアイコン（カーソルではない）を表す値
         */
        private const val TYPE_ICON = 1
        private const val SIDE_WHEN_ZERO = 256
    }
}
