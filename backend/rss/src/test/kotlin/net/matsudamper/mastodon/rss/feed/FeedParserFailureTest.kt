package net.matsudamper.mastodon.rss.feed

import kotlin.test.Test
import kotlin.test.assertFailsWith

// フィードとして読めないものを、読めたことにしないのを確認する。
// 中身は相手のサーバーが返すもので、こちらでは選べない。
// XXE と展開攻撃の入口になるので、外部を読みに行かないことも固定しておく。
class FeedParserFailureTest {
    @Test
    fun `XML でなければ例外にする`() {
        assertFailsWith<FeedParseException> {
            FeedParser.parse("<html><body>フィードではない</body></html>")
        }
    }

    @Test
    fun `閉じていない XML は例外にする`() {
        assertFailsWith<FeedParseException> {
            FeedParser.parse("""<rss version="2.0"><channel><title>題名</title>""")
        }
    }

    @Test
    fun `空の入力は例外にする`() {
        assertFailsWith<FeedParseException> {
            FeedParser.parse("")
        }
    }

    @Test
    fun `外部エンティティを展開しない`() {
        val xml =
            """
            <?xml version="1.0"?>
            <!DOCTYPE rss [
              <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <rss version="2.0">
              <channel>
                <title>&xxe;</title>
              </channel>
            </rss>
            """.trimIndent()

        // DTD を切ってあるので、実体参照は解決できずに例外になる。
        // 読めてしまうと、ローカルのファイルの中身がフィードの題名として入る
        assertFailsWith<FeedParseException> { FeedParser.parse(xml) }
    }

    @Test
    fun `入れ子の実体参照で展開しない`() {
        // billion laughs。DTD を有効にしていると、数行の入力でメモリを食い潰せる
        val xml =
            """
            <?xml version="1.0"?>
            <!DOCTYPE rss [
              <!ENTITY a "aaaaaaaaaa">
              <!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;">
              <!ENTITY c "&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;">
            ]>
            <rss version="2.0">
              <channel>
                <title>&c;</title>
              </channel>
            </rss>
            """.trimIndent()

        assertFailsWith<FeedParseException> { FeedParser.parse(xml) }
    }
}
