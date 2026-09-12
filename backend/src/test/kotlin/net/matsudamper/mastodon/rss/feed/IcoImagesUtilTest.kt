package net.matsudamper.mastodon.rss.feed

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import net.matsudamper.mastodon.rss.TestImageBytes

// ICO コンテナから埋め込み画像を PNG として取り出す部分だけを、FeedIconService から切り離して確かめる
class IcoImagesUtilTest {
    @Test
    fun `埋め込みのPNGはそのまま取り出せる`() {
        val extracted = IcoImagesUtil.extractLargestImageAsPng(TestImageBytes.ICO)

        assertContentEquals(TestImageBytes.PNG + TestImageBytes.PNG_IEND, assertNotNull(extracted))
    }

    @Test
    fun `末尾がIENDでないPNGは取り出さない`() {
        // 署名はあるが IEND チャンクの手前で申告サイズが切れている
        val realPng = TestImageBytes.PNG + "FAKE_CHUNK_DATA".toByteArray() + TestImageBytes.PNG_IEND
        val truncatedSize = realPng.size - 5

        val header = byteArrayOf(0, 0, 1, 0, 1, 0)
        val entries = pngEntryOf(width = 32, height = 32, size = truncatedSize, offset = header.size + ENTRY_SIZE)
        val ico = header + entries + realPng

        assertNull(IcoImagesUtil.extractLargestImageAsPng(ico))
    }

    @Test
    fun `複数のPNGエントリのうち面積が一番大きいものを選ぶ`() {
        val small = TestImageBytes.PNG + TestImageBytes.PNG_IEND
        val large = TestImageBytes.PNG + byteArrayOf(1, 2, 3, 4) + TestImageBytes.PNG_IEND

        val header = byteArrayOf(0, 0, 1, 0, 2, 0)
        val firstOffset = header.size + ENTRY_SIZE * 2
        val secondOffset = firstOffset + small.size
        val entries = pngEntryOf(width = 16, height = 16, size = small.size, offset = firstOffset) +
            pngEntryOf(width = 48, height = 48, size = large.size, offset = secondOffset)

        val ico = header + entries + small + large

        assertContentEquals(large, assertNotNull(IcoImagesUtil.extractLargestImageAsPng(ico)))
    }

    @Test
    fun `32ビットDIBは実アルファをそのまま使って変換できる`() {
        // 2x2。各ピクセルに別々の色と不透明なアルファを持たせる
        val colorData = byteArrayOf(
            // ファイル先頭の行 = 画像の下段: (0,1) → (1,1)
            90, 80, 70, 255.toByte(), 120, 110, 100, 255.toByte(),
            // ファイル 2 行目 = 画像の上段: (0,0) → (1,0)
            30, 20, 10, 255.toByte(), 60, 50, 40, 255.toByte(),
        )
        // 実アルファがある間は無視されるはずのマスク。両ピクセルとも透明にしてある
        val maskData = byteArrayOf(0xC0.toByte(), 0, 0, 0, 0xC0.toByte(), 0, 0, 0)
        val ico = icoWithDib(
            width = 2,
            height = 2,
            bitCount = 32,
            colorsUsed = 0,
            paletteData = ByteArray(0),
            colorData = colorData,
            maskData = maskData,
            compression = 0,
            doubled = true,
        )

        val (width, height, rgba) = decodePngRgba(assertNotNull(IcoImagesUtil.extractLargestImageAsPng(ico)))

        assertEquals(2, width)
        assertEquals(2, height)
        assertContentEquals(
            byteArrayOf(
                10, 20, 30, 255.toByte(), 40, 50, 60, 255.toByte(),
                70, 80, 90, 255.toByte(), 100, 110, 120, 255.toByte(),
            ),
            rgba,
        )
    }

    @Test
    fun `実アルファが無い32ビットDIBはANDマスクで透過を決める`() {
        // 色データのアルファは全ピクセル 0（実アルファ無し扱い）
        val colorData = byteArrayOf(
            90, 80, 70, 0, 120, 110, 100, 0,
            30, 20, 10, 0, 60, 50, 40, 0,
        )
        // 画像左上 (0,0) だけ透明、他は不透明にする
        val maskData = byteArrayOf(0, 0, 0, 0, 0x80.toByte(), 0, 0, 0)
        val ico = icoWithDib(
            width = 2,
            height = 2,
            bitCount = 32,
            colorsUsed = 0,
            paletteData = ByteArray(0),
            colorData = colorData,
            maskData = maskData,
            compression = 0,
            doubled = true,
        )

        val (_, _, rgba) = decodePngRgba(assertNotNull(IcoImagesUtil.extractLargestImageAsPng(ico)))

        assertEquals(0, rgba[3].toInt(), "(0,0) のアルファ")
        assertEquals(255, rgba[7].toInt() and 0xFF, "(1,0) のアルファ")
        assertEquals(255, rgba[11].toInt() and 0xFF, "(0,1) のアルファ")
        assertEquals(255, rgba[15].toInt() and 0xFF, "(1,1) のアルファ")
    }

    @Test
    fun `24ビットDIBはアルファを持たずANDマスクで透過を決める`() {
        // 幅 2、高さ 1。24 ビットは 1 行 6 バイトを 4 バイト境界へ 2 バイットパディングする
        val colorData = byteArrayOf(1, 2, 3, 4, 5, 6, 0, 0)
        // 右のピクセルだけ透明にする
        val maskData = byteArrayOf(0x40, 0, 0, 0)
        val ico = icoWithDib(
            width = 2,
            height = 1,
            bitCount = 24,
            colorsUsed = 0,
            paletteData = ByteArray(0),
            colorData = colorData,
            maskData = maskData,
            compression = 0,
            doubled = true,
        )

        val (width, height, rgba) = decodePngRgba(assertNotNull(IcoImagesUtil.extractLargestImageAsPng(ico)))

        assertEquals(2, width)
        assertEquals(1, height)
        assertContentEquals(
            byteArrayOf(3, 2, 1, 255.toByte(), 6, 5, 4, 0),
            rgba,
        )
    }

    @Test
    fun `8ビットパレットのDIBを変換できる`() {
        // 幅 2、高さ 1。索引 0 は (B10,G20,R30)、索引 1 は (B40,G50,R60)
        val palette = byteArrayOf(10, 20, 30, 0, 40, 50, 60, 0)
        // 左のピクセルは索引 1、右のピクセルは索引 0
        val colorData = byteArrayOf(1, 0, 0, 0)
        // 右のピクセルだけ透明にする
        val maskData = byteArrayOf(0x40, 0, 0, 0)
        val ico = icoWithDib(
            width = 2,
            height = 1,
            bitCount = 8,
            colorsUsed = 2,
            paletteData = palette,
            colorData = colorData,
            maskData = maskData,
            compression = 0,
            doubled = true,
        )

        val (width, height, rgba) = decodePngRgba(assertNotNull(IcoImagesUtil.extractLargestImageAsPng(ico)))

        assertEquals(2, width)
        assertEquals(1, height)
        assertContentEquals(
            byteArrayOf(60, 50, 40, 255.toByte(), 30, 20, 10, 0),
            rgba,
        )
    }

    @Test
    fun `4ビットパレットのDIBを変換できる`() {
        // 幅 2、高さ 1。1 バイトに 2 画素（上位ニブルが先の画素）
        val palette = byteArrayOf(10, 20, 30, 0, 40, 50, 60, 0)
        val colorData = byteArrayOf(0x10, 0, 0, 0)
        val maskData = byteArrayOf(0, 0, 0, 0)
        val ico = icoWithDib(
            width = 2,
            height = 1,
            bitCount = 4,
            colorsUsed = 2,
            paletteData = palette,
            colorData = colorData,
            maskData = maskData,
            compression = 0,
            doubled = true,
        )

        val (_, _, rgba) = decodePngRgba(assertNotNull(IcoImagesUtil.extractLargestImageAsPng(ico)))

        assertContentEquals(
            byteArrayOf(60, 50, 40, 255.toByte(), 30, 20, 10, 255.toByte()),
            rgba,
        )
    }

    @Test
    fun `1ビットパレットのDIBを変換できる`() {
        // 幅 2、高さ 1。1 バイトに 8 画素（上位ビットが先の画素）
        val palette = byteArrayOf(10, 20, 30, 0, 40, 50, 60, 0)
        val colorData = byteArrayOf(0b01000000, 0, 0, 0)
        val maskData = byteArrayOf(0, 0, 0, 0)
        val ico = icoWithDib(
            width = 2,
            height = 1,
            bitCount = 1,
            colorsUsed = 2,
            paletteData = palette,
            colorData = colorData,
            maskData = maskData,
            compression = 0,
            doubled = true,
        )

        val (_, _, rgba) = decodePngRgba(assertNotNull(IcoImagesUtil.extractLargestImageAsPng(ico)))

        assertContentEquals(
            byteArrayOf(30, 20, 10, 255.toByte(), 60, 50, 40, 255.toByte()),
            rgba,
        )
    }

    @Test
    fun `ANDマスクを省いた非2倍のbiHeightは不透明として読める`() {
        // biHeight を実際の高さのまま（2 倍しない）申告するエンコーダの形。マスクは無い
        val colorData = byteArrayOf(90, 80, 70, 0, 120, 110, 100, 0)
        val ico = icoWithDib(
            width = 2,
            height = 1,
            bitCount = 32,
            colorsUsed = 0,
            paletteData = ByteArray(0),
            colorData = colorData,
            maskData = ByteArray(0),
            compression = 0,
            doubled = false,
        )

        val (_, _, rgba) = decodePngRgba(assertNotNull(IcoImagesUtil.extractLargestImageAsPng(ico)))

        assertContentEquals(
            byteArrayOf(70, 80, 90, 255.toByte(), 100, 110, 120, 255.toByte()),
            rgba,
        )
    }

    @Test
    fun `ICONDIRENTRYの高さと矛盾するbiHeightはnull`() {
        val header = byteArrayOf(0, 0, 1, 0, 1, 0)
        val colorData = ByteArray(rowStride(2, 32) * 5)
        val dibHeader = ByteArray(DIB_HEADER_SIZE)
        littleEndianInto(dibHeader, 0, DIB_HEADER_SIZE)
        littleEndianInto(dibHeader, 4, 2)
        // 高さ 3 の 2 倍（6）でも高さそのもの（3）でもない、矛盾した申告
        littleEndianInto(dibHeader, 8, 5)
        littleEndian16Into(dibHeader, 12, 1)
        littleEndian16Into(dibHeader, 14, 32)
        littleEndianInto(dibHeader, 16, 0)
        val dib = dibHeader + colorData

        val imageOffset = header.size + ENTRY_SIZE
        val entry = byteArrayOf(2, 3, 0, 0, 1, 0, 32, 0) + littleEndian(dib.size) + littleEndian(imageOffset)
        val ico = header + entry + dib

        assertNull(IcoImagesUtil.extractLargestImageAsPng(ico))
    }

    @Test
    fun `圧縮されたDIBは変換できない`() {
        val colorData = ByteArray(rowStride(2, 32) * 2)
        val maskData = ByteArray(rowStride(2, 1) * 2)
        val ico = icoWithDib(
            width = 2,
            height = 2,
            bitCount = 32,
            colorsUsed = 0,
            paletteData = ByteArray(0),
            colorData = colorData,
            maskData = maskData,
            compression = 1,
            doubled = true,
        )

        assertNull(IcoImagesUtil.extractLargestImageAsPng(ico))
    }

    @Test
    fun `どちらの形式でもない短いエントリはnull`() {
        val header = byteArrayOf(0, 0, 1, 0, 1, 0)
        val entries = pngEntryOf(width = 32, height = 32, size = 8, offset = header.size + ENTRY_SIZE)
        val ico = header + entries + byteArrayOf(0x28, 0, 0, 0, 32, 0, 0, 0)

        assertNull(IcoImagesUtil.extractLargestImageAsPng(ico))
    }

    @Test
    fun `ヘッダーが短すぎればnull`() {
        assertNull(IcoImagesUtil.extractLargestImageAsPng(byteArrayOf(0, 0, 1, 0)))
    }

    @Test
    fun `ICOではない種類のヘッダーはnull`() {
        // type が 2（カーソル）
        val header = byteArrayOf(0, 0, 2, 0, 0, 0)
        assertNull(IcoImagesUtil.extractLargestImageAsPng(header))
    }

    @Test
    fun `申告した範囲がファイルをはみ出すエントリはnull`() {
        val header = byteArrayOf(0, 0, 1, 0, 1, 0)
        // size を実際のバイト列より大きく申告する
        val entries = pngEntryOf(width = 32, height = 32, size = 1_000, offset = header.size + ENTRY_SIZE)
        val ico = header + entries + TestImageBytes.PNG + TestImageBytes.PNG_IEND

        assertNull(IcoImagesUtil.extractLargestImageAsPng(ico))
    }

    private fun pngEntryOf(
        width: Int,
        height: Int,
        size: Int,
        offset: Int,
    ): ByteArray = byteArrayOf(
        width.toByte(),
        height.toByte(),
        0,
        0,
        1,
        0,
        32,
        0,
    ) + littleEndian(size) + littleEndian(offset)

    /**
     * DIB（BMP）1 枚だけを埋め込んだ ICO コンテナ。
     *
     * [paletteData] [colorData] [maskData] は呼び出し側が [rowStride] に合わせて
     * パディング済みのものを渡す。24 / 32 ビットでは [paletteData] は空にする
     */
    private fun icoWithDib(
        width: Int,
        height: Int,
        bitCount: Int,
        colorsUsed: Int,
        paletteData: ByteArray,
        colorData: ByteArray,
        maskData: ByteArray,
        compression: Int,
        doubled: Boolean,
    ): ByteArray {
        val header = byteArrayOf(0, 0, 1, 0, 1, 0)
        val dibHeader = ByteArray(DIB_HEADER_SIZE)
        littleEndianInto(dibHeader, 0, DIB_HEADER_SIZE)
        littleEndianInto(dibHeader, 4, width)
        littleEndianInto(dibHeader, 8, if (doubled) height * 2 else height)
        littleEndian16Into(dibHeader, 12, 1)
        littleEndian16Into(dibHeader, 14, bitCount)
        littleEndianInto(dibHeader, 16, compression)
        littleEndianInto(dibHeader, 32, colorsUsed)
        val dib = dibHeader + paletteData + colorData + maskData

        val imageOffset = header.size + ENTRY_SIZE
        val entry = byteArrayOf(width.toByte(), height.toByte(), 0, 0, 1, 0, bitCount.toByte(), 0) +
            littleEndian(dib.size) + littleEndian(imageOffset)

        return header + entry + dib
    }

    private fun rowStride(
        width: Int,
        bitsPerPixel: Int,
    ): Int = ((width * bitsPerPixel + 31) / 32) * 4

    private fun littleEndian(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private fun littleEndianInto(
        target: ByteArray,
        offset: Int,
        value: Int,
    ) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value shr 8) and 0xFF).toByte()
        target[offset + 2] = ((value shr 16) and 0xFF).toByte()
        target[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun littleEndian16Into(
        target: ByteArray,
        offset: Int,
        value: Int,
    ) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    /**
     * [PngEncoder] が組み立てた PNG を読み戻す。IHDR と IDAT だけを見る、この
     * テスト専用の最小限のデコーダー（フィルタ無し・8 ビット RGBA だけを前提にする）
     */
    private fun decodePngRgba(png: ByteArray): Triple<Int, Int, ByteArray> {
        var pos = 8
        var width = 0
        var height = 0
        val idat = ByteArrayOutputStream()
        while (pos < png.size) {
            val length = readU32BE(png, pos)
            val type = String(png, pos + 4, 4, Charsets.US_ASCII)
            val dataStart = pos + 8
            when (type) {
                "IHDR" -> {
                    width = readU32BE(png, dataStart)
                    height = readU32BE(png, dataStart + 4)
                }

                "IDAT" -> idat.write(png, dataStart, length)
            }
            pos = dataStart + length + 4
        }

        val inflater = Inflater()
        inflater.setInput(idat.toByteArray())
        val stride = width * 4
        val raw = ByteArray(height * (1 + stride))
        var written = 0
        while (written < raw.size) {
            written += inflater.inflate(raw, written, raw.size - written)
        }

        val rgba = ByteArray(width * height * 4)
        for (row in 0 until height) {
            System.arraycopy(raw, row * (1 + stride) + 1, rgba, row * stride, stride)
        }
        return Triple(width, height, rgba)
    }

    private fun readU32BE(
        bytes: ByteArray,
        offset: Int,
    ): Int = ((bytes[offset].toInt() and 0xFF) shl 24) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
        (bytes[offset + 3].toInt() and 0xFF)

    private companion object {
        const val ENTRY_SIZE = 16
        const val DIB_HEADER_SIZE = 40
    }
}
