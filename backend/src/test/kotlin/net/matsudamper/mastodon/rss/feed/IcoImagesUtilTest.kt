package net.matsudamper.mastodon.rss.feed

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import net.matsudamper.mastodon.rss.TestImageBytes

// ICO コンテナから埋め込みの PNG を取り出す部分だけを、FeedIconService から切り離して確かめる
class IcoImagesUtilTest {
    @Test
    fun `埋め込みのPNGを取り出せる`() {
        val extracted = IcoImagesUtil.extractLargestPng(TestImageBytes.ICO)

        assertContentEquals(TestImageBytes.PNG, assertNotNull(extracted))
    }

    @Test
    fun `複数エントリのうち面積が一番大きいものを選ぶ`() {
        val small = TestImageBytes.PNG + byteArrayOf(1)
        val large = TestImageBytes.PNG + byteArrayOf(1, 2, 3, 4)

        val header = byteArrayOf(0, 0, 1, 0, 2, 0)
        val firstOffset = header.size + ENTRY_SIZE * 2
        val secondOffset = firstOffset + small.size
        val entries = entryOf(width = 16, height = 16, size = small.size, offset = firstOffset) +
            entryOf(width = 48, height = 48, size = large.size, offset = secondOffset)

        val ico = header + entries + small + large

        assertContentEquals(large, assertNotNull(IcoImagesUtil.extractLargestPng(ico)))
    }

    @Test
    fun `PNGが埋め込まれていなければnull`() {
        // エントリはあるが、指す先が PNG 署名で始まらない（BMP 埋め込み相当）
        val header = byteArrayOf(0, 0, 1, 0, 1, 0)
        val entries = entryOf(width = 32, height = 32, size = 8, offset = header.size + ENTRY_SIZE)
        val ico = header + entries + byteArrayOf(0x28, 0, 0, 0, 32, 0, 0, 0)

        assertNull(IcoImagesUtil.extractLargestPng(ico))
    }

    @Test
    fun `ヘッダーが短すぎればnull`() {
        assertNull(IcoImagesUtil.extractLargestPng(byteArrayOf(0, 0, 1, 0)))
    }

    @Test
    fun `ICOではない種類のヘッダーはnull`() {
        // type が 2（カーソル）
        val header = byteArrayOf(0, 0, 2, 0, 0, 0)
        assertNull(IcoImagesUtil.extractLargestPng(header))
    }

    @Test
    fun `申告した範囲がファイルをはみ出すエントリはnull`() {
        val header = byteArrayOf(0, 0, 1, 0, 1, 0)
        // size を実際のバイト列より大きく申告する
        val entries = entryOf(width = 32, height = 32, size = 1_000, offset = header.size + ENTRY_SIZE)
        val ico = header + entries + TestImageBytes.PNG

        assertNull(IcoImagesUtil.extractLargestPng(ico))
    }

    private fun entryOf(
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

    private fun littleEndian(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private companion object {
        const val ENTRY_SIZE = 16
    }
}
