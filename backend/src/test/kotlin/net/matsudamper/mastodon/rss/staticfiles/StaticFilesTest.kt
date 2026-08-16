package net.matsudamper.mastodon.rss.staticfiles

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import io.ktor.http.ContentType

class StaticFilesTest {
    /** 配信ディレクトリの外にファイルを置きたいので、1 段上を作っておく */
    private val base: Path = Files.createTempDirectory("static-files-test")
    private val root: Path = base.resolve("dist").also { it.createDirectories() }

    @AfterTest
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun tearDown() {
        base.deleteRecursively()
    }

    @Test
    fun `rootはindex_htmlを返す`() {
        val index = putFile("index.html", "<html></html>")

        assertEquals(index, staticFiles().resolve(emptyList()))
    }

    @Test
    fun `ファイルがあればそれを返す`() {
        putFile("index.html", "<html></html>")
        val script = putFile("frontend.js", "console.log()")

        assertEquals(script, staticFiles().resolve(listOf("frontend.js")))
    }

    @Test
    fun `サブディレクトリのファイルも返す`() {
        val font = putFile("fonts/main.woff2", "font")

        assertEquals(font, staticFiles().resolve(listOf("fonts", "main.woff2")))
    }

    @Test
    fun `拡張子の無いパスは画面のパスとしてindex_htmlを返す`() {
        val index = putFile("index.html", "<html></html>")

        assertEquals(index, staticFiles().resolve(listOf("admin", "password-hash")))
    }

    @Test
    fun `拡張子のあるパスはindex_htmlに落とさず見つからないとする`() {
        putFile("index.html", "<html></html>")

        assertNull(staticFiles().resolve(listOf("missing.js")))
    }

    @Test
    fun `アカウントのパスはindex_htmlを返す`() {
        val index = putFile("index.html", "<html></html>")

        assertEquals(index, staticFiles().resolve(listOf("@feed1")))
    }

    @Test
    fun `ユーザー名にドットが入っていても拡張子として扱わない`() {
        val index = putFile("index.html", "<html></html>")

        assertEquals(index, staticFiles().resolve(listOf("@feed1.2")))
    }

    @Test
    fun `index_htmlが無ければ画面のパスも見つからないとする`() {
        putFile("frontend.js", "console.log()")

        assertNull(staticFiles().resolve(listOf("admin")))
    }

    @Test
    fun `親ディレクトリを辿るパスは読ませない`() {
        putFile("index.html", "<html></html>")
        Files.writeString(base.resolve("secret.txt"), "secret")

        assertNull(staticFiles().resolve(listOf("..", "secret.txt")))
    }

    @Test
    fun `途中に親ディレクトリを挟んでも読ませない`() {
        putFile("index.html", "<html></html>")
        Files.writeString(base.resolve("secret.txt"), "secret")

        assertNull(staticFiles().resolve(listOf("fonts", "..", "..", "secret.txt")))
    }

    @Test
    fun `空のセグメントは無視してファイルを引く`() {
        val script = putFile("frontend.js", "console.log()")

        assertEquals(script, staticFiles().resolve(listOf("", "frontend.js", "")))
    }

    @Test
    fun `ディレクトリそのものは返さない`() {
        val index = putFile("index.html", "<html></html>")
        putFile("fonts/main.woff2", "font")

        // 拡張子が無いので画面のパスと同じ扱いになる
        assertEquals(index, staticFiles().resolve(listOf("fonts")))
    }

    @Test
    fun `ディレクトリが無ければ配信できないとする`() {
        assertEquals(false, StaticFiles(root.resolve("not-exists")).isAvailable())
    }

    @Test
    fun `wasmはapplication_wasmで返す`() {
        assertEquals(ContentType.Application.Wasm, StaticFiles.contentTypeOf("frontend.wasm"))
    }

    @Test
    fun `拡張子から Content-Type を決める`() {
        assertEquals(ContentType.Text.Html, StaticFiles.contentTypeOf("index.html"))
        assertEquals(ContentType.Application.JavaScript, StaticFiles.contentTypeOf("frontend.js"))
        assertEquals(ContentType.Font.Woff2, StaticFiles.contentTypeOf("main.WOFF2"))
        assertEquals(ContentType.Application.OctetStream, StaticFiles.contentTypeOf("unknown"))
    }

    private fun staticFiles(): StaticFiles = StaticFiles(root)

    private fun putFile(
        relativePath: String,
        content: String,
    ): Path {
        val path = root.resolve(relativePath)
        path.parent.createDirectories()
        path.writeText(content)
        return path.toAbsolutePath().normalize()
    }
}
