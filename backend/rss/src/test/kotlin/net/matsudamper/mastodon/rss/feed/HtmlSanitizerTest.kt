package net.matsudamper.mastodon.rss.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// 配信元の HTML を削る処理を確認する。
// 通してはいけないものが残っていないことが要点なので、
// 「残るもの」より「消えるもの」を厚く見る。
class HtmlSanitizerTest {
    @Test
    fun `許可したタグは残す`() {
        val html = """<p>本文の<a href="https://example.com/1">リンク</a>と<br>改行</p>"""

        assertEquals(html, HtmlSanitizer.sanitize(html))
    }

    @Test
    fun `許可していないタグは落として中身は残す`() {
        val html = "<div><h1>見出し</h1><p>本文</p></div>"

        assertEquals("見出し<p>本文</p>", HtmlSanitizer.sanitize(html))
    }

    @Test
    fun `script は中身ごと落とす`() {
        val html = """<p>本文</p><script>alert("あ")</script><p>続き</p>"""

        assertEquals("<p>本文</p><p>続き</p>", HtmlSanitizer.sanitize(html))
    }

    @Test
    fun `style は中身ごと落とす`() {
        val html = "<style>p { color: red }</style><p>本文</p>"

        assertEquals("<p>本文</p>", HtmlSanitizer.sanitize(html))
    }

    @Test
    fun `許可していない属性は落とす`() {
        val html = """<p style="color:red" onclick="alert(1)">本文</p>"""

        assertEquals("<p>本文</p>", HtmlSanitizer.sanitize(html))
    }

    @Test
    fun `javascript スキームのリンクは href ごと落とす`() {
        val html = """<a href="javascript:alert(1)">押す</a>"""

        val sanitized = HtmlSanitizer.sanitize(html)

        assertEquals("<a>押す</a>", sanitized)
        assertFalse(sanitized.contains("javascript"), "スキームが残っている: $sanitized")
    }

    @Test
    fun `実体参照で隠した javascript スキームも落とす`() {
        val html = """<a href="&#106;avascript:alert(1)">押す</a>"""

        assertEquals("<a>押す</a>", HtmlSanitizer.sanitize(html))
    }

    @Test
    fun `相対リンクは残す`() {
        val html = """<a href="/articles/1">記事</a>"""

        assertEquals(html, HtmlSanitizer.sanitize(html))
    }

    @Test
    fun `閉じていないタグを末尾で閉じる`() {
        assertEquals("<p>本文</p>", HtmlSanitizer.sanitize("<p>本文"))
    }

    @Test
    fun `対応しない閉じタグは落とす`() {
        assertEquals("本文", HtmlSanitizer.sanitize("本文</p>"))
    }

    @Test
    fun `入れ子が交差していても閉じ忘れない`() {
        // <a> の中で <p> が閉じられていない。そのまま出すと受信側の表示が崩れる
        val sanitized = HtmlSanitizer.sanitize("""<a href="https://example.com"><p>本文</a>続き""")

        assertEquals("""<a href="https://example.com"><p>本文</p></a>続き""", sanitized)
    }

    @Test
    fun `タグではない不等号はエスケープする`() {
        assertEquals("a &lt; b &gt; c", HtmlSanitizer.sanitize("a < b > c"))
    }

    @Test
    fun `実体参照はそのまま残し、裸のアンパサンドはエスケープする`() {
        assertEquals("&amp;lt; &amp; &#39;", HtmlSanitizer.sanitize("&amp;lt; & &#39;"))
    }

    @Test
    fun `コメントは落とす`() {
        assertEquals("<p>本文</p>", HtmlSanitizer.sanitize("<!-- 消える -->" + "<p>本文</p>"))
    }

    @Test
    fun `プレーンテキストにするとタグが消えて実体参照が戻る`() {
        val html = """<p>本文の<a href="https://example.com">リンク</a></p><p>2 段落目 &amp; 続き</p>"""

        // 段落の切れ目は空行として残す
        assertEquals("本文のリンク\n\n2 段落目 & 続き", HtmlSanitizer.toPlainText(html))
    }

    @Test
    fun `br は改行にする`() {
        assertEquals("1 行目\n2 行目", HtmlSanitizer.toPlainText("1 行目<br>2 行目"))
    }

    @Test
    fun `数値の実体参照を戻す`() {
        assertEquals("あA", HtmlSanitizer.decodeEntities("&#12354;&#x41;"))
    }

    @Test
    fun `知らない実体参照はそのまま残す`() {
        assertEquals("&unknown;", HtmlSanitizer.decodeEntities("&unknown;"))
    }

    @Test
    fun `テキストのエスケープは実体参照を解釈しない`() {
        assertEquals("&amp;amp; &lt;p&gt;", HtmlSanitizer.escapeText("&amp; <p>"))
    }
}
