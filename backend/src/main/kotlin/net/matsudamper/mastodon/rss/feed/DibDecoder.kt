package net.matsudamper.mastodon.rss.feed

/**
 * ICO に埋め込まれた無圧縮の BMP（DIB）を RGBA に展開する。
 *
 * ICO の DIB は BITMAPFILEHEADER を持たず BITMAPINFOHEADER（40 バイト）から始まる。
 * パレット（1/4/8 ビット）と直接指定（24/32 ビット）を扱い、圧縮された DIB や
 * BITMAPINFOHEADER 以外のヘッダー形式は対応しない
 */
internal object DibDecoder {
    class Image(
        val width: Int,
        val height: Int,
        /**
         * 上から下、1 ピクセルあたり R, G, B, A の並び
         */
        val rgba: ByteArray,
    )

    /**
     * 幅・高さは ICONDIRENTRY の申告と一致するものだけを受け付ける。高さは仕様上
     * 実際の高さの 2 倍（色データ分 + AND マスク分）を申告するが、マスクを省いて
     * 実際の高さをそのまま書くエンコーダもあるため、その場合はマスク無しとして読む。
     * どちらとも一致しなければ壊れているとみなして null
     */
    fun decode(
        bytes: ByteArray,
        entry: IcoDirectory.Entry,
    ): Image? {
        val dibOffset = entry.imageOffset
        val entrySize = entry.imageSize
        if (entrySize < DIB_HEADER_SIZE || dibOffset + DIB_HEADER_SIZE > bytes.size) return null
        if (LittleEndianBytes.u32(bytes, dibOffset) != DIB_HEADER_SIZE.toLong()) return null

        val width = LittleEndianBytes.u32(bytes, dibOffset + WIDTH_OFFSET)
        if (width != entry.width.toLong()) return null

        val declaredHeight = LittleEndianBytes.u32(bytes, dibOffset + HEIGHT_OFFSET)
        val hasEmbeddedMask = when (declaredHeight) {
            entry.height.toLong() * 2 -> true
            entry.height.toLong() -> false
            else -> return null
        }

        val planes = LittleEndianBytes.u16(bytes, dibOffset + PLANES_OFFSET)
        val bitCount = LittleEndianBytes.u16(bytes, dibOffset + BIT_COUNT_OFFSET)
        val compression = LittleEndianBytes.u32(bytes, dibOffset + COMPRESSION_OFFSET)

        if (planes != 1) return null
        if (bitCount !in SUPPORTED_BIT_COUNTS) return null
        if (compression != 0L) return null

        val w = entry.width
        val h = entry.height
        val entryEnd = dibOffset.toLong() + entrySize

        val paletteOffset = dibOffset + DIB_HEADER_SIZE
        val paletteCount = paletteCountOf(bytes, dibOffset, bitCount)
        val paletteEnd = paletteOffset.toLong() + paletteCount.toLong() * PALETTE_ENTRY_SIZE
        if (paletteEnd > bytes.size.toLong() || paletteEnd > entryEnd) return null

        val colorStride = rowStride(w, bitCount)
        val colorStart = paletteEnd.toInt()
        val colorEnd = colorStart.toLong() + colorStride.toLong() * h
        if (colorEnd > bytes.size.toLong() || colorEnd > entryEnd) return null

        val rgba = ByteArray(w * h * 4)
        for (row in 0 until h) {
            val srcRowStart = colorStart + row * colorStride
            val dstRow = h - 1 - row
            for (col in 0 until w) {
                val (r, g, b) = colorAt(bytes, srcRowStart, col, bitCount, paletteOffset)
                val a = if (bitCount == BIT_COUNT_ARGB32) {
                    bytes[srcRowStart + col * 4 + 3].toInt() and 0xFF
                } else {
                    OPAQUE
                }

                val dstIndex = (dstRow * w + col) * 4
                rgba[dstIndex] = r.toByte()
                rgba[dstIndex + 1] = g.toByte()
                rgba[dstIndex + 2] = b.toByte()
                rgba[dstIndex + 3] = a.toByte()
            }
        }

        val maskStride = rowStride(w, 1)
        val maskStart = colorEnd.toInt()
        val maskEnd = maskStart.toLong() + maskStride.toLong() * h
        val hasMask = hasEmbeddedMask && maskEnd <= bytes.size.toLong() && maskEnd <= entryEnd

        // 32 ビットは実アルファを持つことがある。全ピクセルで 0 なら実アルファ無しとみなし、
        // AND マスクの方を使う（さもないと画像全体が透明に見える）
        if (bitCount != BIT_COUNT_ARGB32 || !hasRealAlpha(rgba)) {
            applyAndMask(bytes, maskStart, maskStride, w, h, hasMask, rgba)
        }

        return Image(w, h, rgba)
    }

    /**
     * パレットの色数。biClrUsed が 1 件以上ビット数の上限以下ならその値、
     * それ以外（0 や不正な申告値）はビット数から決まる上限をそのまま使う。
     * 24 / 32 ビットにパレットは無い
     */
    private fun paletteCountOf(
        bytes: ByteArray,
        dibOffset: Int,
        bitCount: Int,
    ): Int {
        if (bitCount == BIT_COUNT_RGB24 || bitCount == BIT_COUNT_ARGB32) return 0

        val maxColors = 1 shl bitCount
        val declaredColors = LittleEndianBytes.u32(bytes, dibOffset + COLORS_USED_OFFSET)
        return if (declaredColors in 1..maxColors.toLong()) declaredColors.toInt() else maxColors
    }

    /**
     * この位置の画素の色。パレット形式なら索引を引き、直接指定形式ならそのまま読む
     */
    private fun colorAt(
        bytes: ByteArray,
        rowStart: Int,
        col: Int,
        bitCount: Int,
        paletteOffset: Int,
    ): Triple<Int, Int, Int> = when (bitCount) {
        BIT_COUNT_RGB24, BIT_COUNT_ARGB32 -> directColorAt(bytes, rowStart, col, bitCount)
        else -> paletteColorAt(bytes, paletteOffset, colorIndexAt(bytes, rowStart, col, bitCount))
    }

    private fun directColorAt(
        bytes: ByteArray,
        rowStart: Int,
        col: Int,
        bitCount: Int,
    ): Triple<Int, Int, Int> {
        val srcPixel = rowStart + col * (bitCount / 8)
        val b = bytes[srcPixel].toInt() and 0xFF
        val g = bytes[srcPixel + 1].toInt() and 0xFF
        val r = bytes[srcPixel + 2].toInt() and 0xFF
        return Triple(r, g, b)
    }

    private fun paletteColorAt(
        bytes: ByteArray,
        paletteOffset: Int,
        index: Int,
    ): Triple<Int, Int, Int> {
        val entry = paletteOffset + index * PALETTE_ENTRY_SIZE
        val b = bytes[entry].toInt() and 0xFF
        val g = bytes[entry + 1].toInt() and 0xFF
        val r = bytes[entry + 2].toInt() and 0xFF
        return Triple(r, g, b)
    }

    /**
     * パレット索引。1 ビットは 8 画素、4 ビットは 2 画素を 1 バイトに詰め、
     * どちらも上位ビットが先の画素を表す
     */
    private fun colorIndexAt(
        bytes: ByteArray,
        rowStart: Int,
        col: Int,
        bitCount: Int,
    ): Int = when (bitCount) {
        1 -> {
            val byteIndex = rowStart + col / 8
            val bit = 7 - (col % 8)
            (bytes[byteIndex].toInt() ushr bit) and 0x1
        }

        4 -> {
            val byteIndex = rowStart + col / 2
            val packed = bytes[byteIndex].toInt() and 0xFF
            if (col % 2 == 0) (packed ushr 4) and 0xF else packed and 0xF
        }

        else -> bytes[rowStart + col].toInt() and 0xFF
    }

    private fun hasRealAlpha(rgba: ByteArray): Boolean = (3 until rgba.size step 4).any { index -> rgba[index].toInt() != 0 }

    private fun applyAndMask(
        bytes: ByteArray,
        maskStart: Int,
        maskStride: Int,
        width: Int,
        height: Int,
        hasMask: Boolean,
        rgba: ByteArray,
    ) {
        if (!hasMask) {
            for (index in rgba.indices step 4) rgba[index + 3] = OPAQUE.toByte()
            return
        }
        for (row in 0 until height) {
            val dstRow = height - 1 - row
            val rowStart = maskStart + row * maskStride
            for (col in 0 until width) {
                val byteIndex = rowStart + col / 8
                val bit = 7 - (col % 8)
                val transparent = (bytes[byteIndex].toInt() ushr bit) and 1 == 1
                rgba[(dstRow * width + col) * 4 + 3] = if (transparent) 0 else OPAQUE.toByte()
            }
        }
    }

    /**
     * 1 行のバイト数。4 バイト境界に切り上げる
     */
    private fun rowStride(
        width: Int,
        bitsPerPixel: Int,
    ): Int = ((width * bitsPerPixel + 31) / 32) * 4

    /**
     * BITMAPINFOHEADER の大きさ。これ以外のヘッダー形式は扱わない
     */
    private const val DIB_HEADER_SIZE = 40
    private const val WIDTH_OFFSET = 4
    private const val HEIGHT_OFFSET = 8
    private const val PLANES_OFFSET = 12
    private const val BIT_COUNT_OFFSET = 14
    private const val COMPRESSION_OFFSET = 16
    private const val COLORS_USED_OFFSET = 32
    private const val PALETTE_ENTRY_SIZE = 4
    private const val BIT_COUNT_RGB24 = 24
    private const val BIT_COUNT_ARGB32 = 32
    private val SUPPORTED_BIT_COUNTS = setOf(1, 4, 8, BIT_COUNT_RGB24, BIT_COUNT_ARGB32)
    private const val OPAQUE = 0xFF
}
