package net.matsudamper.mastodon.rss.linkpreview

import kotlin.test.Test
import kotlin.test.assertEquals

class OgpParserTest {
    @Test
    fun 本文のリンクを出てくる順に重複を除いて返す() {
        val html = """<p>記事<br><a href="https://example.com/a?x=1&amp;y=2">a</a>""" +
            """<a href="https://example.com/b">b</a><a href="https://example.com/a?x=1&amp;y=2">a</a>""" +
            """<a href="mailto:someone@example.com">m</a></p>"""

        assertEquals(
            listOf("https://example.com/a?x=1&y=2", "https://example.com/b"),
            OgpParser.links(html),
        )
    }

    @Test
    fun ogの値を読み相対の画像はページの場所で解決する() {
        val html = """<html><head><title>無視される</title>""" +
            """<meta property="og:title" content="記事 &amp; タイトル">""" +
            """<meta content="サイト" property="og:site_name">""" +
            """<meta property='og:image' content='/img/a.png'></head></html>"""

        assertEquals(
            OgpParser.Ogp(
                title = "記事 & タイトル",
                siteName = "サイト",
                imageUrl = "https://example.com/img/a.png",
            ),
            OgpParser.parse(html = html, pageUrl = "https://example.com/posts/1"),
        )
    }

    @Test
    fun ogが無ければtitle要素を使う() {
        val html = """<html><head><title> ページ </title></head></html>"""

        assertEquals(
            OgpParser.Ogp(title = "ページ", siteName = null, imageUrl = null),
            OgpParser.parse(html = html, pageUrl = "https://example.com/"),
        )
    }

    @Test
    fun http以外の画像は捨てる() {
        val html = """<meta property="og:image" content="javascript:alert(1)">"""

        assertEquals(null, OgpParser.parse(html = html, pageUrl = "https://example.com/").imageUrl)
    }

    @Test
    fun metaで宣言された文字コードを読む() {
        assertEquals("Shift_JIS", OgpParser.declaredCharset("""<meta charset="Shift_JIS">"""))
        assertEquals(
            "EUC-JP",
            OgpParser.declaredCharset("""<meta http-equiv="Content-Type" content="text/html; charset=EUC-JP">"""),
        )
    }
}
