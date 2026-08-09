package net.matsudamper.mastodon.rss.feed

import kotlin.test.Test
import kotlin.test.assertEquals

// 文字列の整形を確認する。
// XML の要素の中身にはインデントの空白と改行がそのまま入るので、
// 取り出した値をそのまま投稿すると見た目が崩れる。
class FeedTextTest {
    @Test
    fun `連続した空白を 1 個にする`() {
        assertEquals("題名 と 続き", FeedText.normalizeWhitespace("  題名   と \t 続き  "))
    }

    @Test
    fun `改行は残す`() {
        assertEquals("1 行目\n2 行目", FeedText.normalizeWhitespace("1 行目\r\n2 行目"))
    }

    @Test
    fun `続いた空行は 1 行にまとめる`() {
        assertEquals("1 行目\n\n2 行目", FeedText.normalizeWhitespace("1 行目\n\n\n\n2 行目"))
    }

    @Test
    fun `1 行にすると改行が空白になる`() {
        assertEquals("1 行目 2 行目", FeedText.singleLine("1 行目\n\n2 行目"))
    }

    @Test
    fun `上限を超えなければそのまま返す`() {
        assertEquals("あいうえお", FeedText.truncate("あいうえお", 5))
    }

    @Test
    fun `上限を超えたら省略記号を付けて切る`() {
        val truncated = FeedText.truncate("あいうえおかきくけこ", 5)

        assertEquals("あいうえ…", truncated)
        assertEquals(5, truncated.length)
    }

    @Test
    fun `単語の途中で切らずに空白まで戻す`() {
        assertEquals("alpha…", FeedText.truncate("alpha bravo charlie", 10))
    }

    @Test
    fun `切る位置が空白なら戻さない`() {
        assertEquals("alpha bravo…", FeedText.truncate("alpha bravo charlie", 12))
    }

    @Test
    fun `サロゲートペアの途中では切らない`() {
        // 𠮷 はサロゲートペア。String.length で数えて切ると壊れた文字になる
        val truncated = FeedText.truncate("𠮷𠮷𠮷𠮷𠮷", 3)

        assertEquals("𠮷𠮷…", truncated)
        assertEquals(3, truncated.codePointCount(0, truncated.length))
    }

    @Test
    fun `省略記号が上限に収まらないなら本文だけを入れる`() {
        assertEquals("あ", FeedText.truncate("あいうえお", 1, ellipsis = "……"))
    }
}
