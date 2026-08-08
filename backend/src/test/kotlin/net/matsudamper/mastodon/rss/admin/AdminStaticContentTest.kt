package net.matsudamper.mastodon.rss.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 静的ファイルの読み出し。リクエストのパスがそのままリソース名になるので、
// 外に出ようとするパスを弾けていることを確かめる。
class AdminStaticContentTest {
    private val content = AdminStaticContent(basePackage = "admin-test-static")

    @Test
    fun `ファイルを読める`() {
        val file = assertNotNull(content.read(listOf("frontend.js")))

        assertTrue(file.bytes.toString(Charsets.UTF_8).contains("テスト用"))
        assertEquals("text/javascript", file.contentType.withoutParameters().toString())
    }

    @Test
    fun `パスが空なら index を返す`() {
        val file = assertNotNull(content.read(emptyList()))

        assertEquals("text/html", file.contentType.withoutParameters().toString())
    }

    @Test
    fun `wasm は application_wasm で返す`() {
        // ここが application/octet-stream だとブラウザが実行を拒否し、画面が真っ白になる
        val file = assertNotNull(content.read(listOf("assets", "frontend.wasm")))

        assertEquals("application/wasm", file.contentType.toString())
    }

    @Test
    fun `無いファイルは null`() {
        assertNull(content.read(listOf("存在しない.js")))
    }

    @Test
    fun `上の階層に出ようとするパスは弾く`() {
        // 通すとバイナリの中の任意のリソースを読み出せてしまう
        assertNull(content.read(listOf("..", "logback.xml")))
        assertNull(content.read(listOf(".", "index.html")))
        assertNull(content.read(listOf("..\\index.html")))
        assertNull(content.read(listOf("../index.html")))
    }

    @Test
    fun `知らない拡張子は octet_stream にする`() {
        assertEquals("application/octet-stream", AdminStaticContent.contentTypeOf("data.unknown").toString())
        assertEquals("application/octet-stream", AdminStaticContent.contentTypeOf("拡張子なし").toString())
    }

    @Test
    fun `拡張子の大文字小文字は区別しない`() {
        assertEquals("text/html", AdminStaticContent.contentTypeOf("INDEX.HTML").withoutParameters().toString())
    }
}
