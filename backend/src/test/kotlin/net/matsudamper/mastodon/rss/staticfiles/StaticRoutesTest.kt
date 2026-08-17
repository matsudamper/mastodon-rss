package net.matsudamper.mastodon.rss.staticfiles

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.matsudamper.mastodon.rss.TestServerEnv
import net.matsudamper.mastodon.rss.module
import net.matsudamper.mastodon.rss.testDependencies

// root から静的ファイルを配信するところを確認する。
// パスの解決そのものは StaticFilesTest 側で見るので、ここは HTTP から見た形だけ。
class StaticRoutesTest {
    private val root: Path = Files.createTempDirectory("static-routes-test")

    @AfterTest
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `rootにアクセスするとindex_htmlが返る`() =
        testApplication {
            putFile("index.html", "<html><body>Hello World</body></html>")
            applicationWith(root)

            val response = client.get("/")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("<html><body>Hello World</body></html>", response.bodyAsText())
            assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
        }

    @Test
    fun `wasmはapplication_wasmで返る`() =
        testApplication {
            putFile("index.html", "<html></html>")
            putFile("frontend.wasm", "wasm")
            applicationWith(root)

            val response = client.get("/frontend.wasm")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Wasm, response.contentType()?.withoutParameters())
        }

    @Test
    fun `画面のパスはindex_htmlが返る`() =
        testApplication {
            putFile("index.html", "<html></html>")
            applicationWith(root)

            val response = client.get("/admin/password-hash")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("<html></html>", response.bodyAsText())
        }

    @Test
    fun `index_htmlはキャッシュされない`() =
        testApplication {
            putFile("index.html", "<html></html>")
            applicationWith(root)

            val response = client.get("/")

            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        }

    @Test
    fun `画面のパスで返すindex_htmlもキャッシュされない`() =
        testApplication {
            putFile("index.html", "<html></html>")
            applicationWith(root)

            val response = client.get("/admin/accounts")

            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        }

    @Test
    fun `名前にハッシュが入るjsとwasmは長くキャッシュされる`() =
        testApplication {
            putFile("index.html", "<html></html>")
            putFile("frontend.0123456789abcdef.js", "console.log()")
            putFile("0123456789abcdef.wasm", "wasm")
            applicationWith(root)

            val expected = "public, max-age=31536000, immutable"
            assertEquals(expected, client.get("/frontend.0123456789abcdef.js").headers[HttpHeaders.CacheControl])
            assertEquals(expected, client.get("/0123456789abcdef.wasm").headers[HttpHeaders.CacheControl])
        }

    @Test
    fun `名前が変わらないファイルにはキャッシュの指示を付けない`() =
        testApplication {
            putFile("index.html", "<html></html>")
            putFile("fonts/NotoSansJP-Regular.ttf", "ttf")
            applicationWith(root)

            val response = client.get("/fonts/NotoSansJP-Regular.ttf")

            assertEquals(null, response.headers[HttpHeaders.CacheControl])
        }

    @Test
    fun `アカウントの画面のパスはindex_htmlが返る`() =
        testApplication {
            putFile("index.html", "<html></html>")
            applicationWith(root)

            val response = client.get("/@feed1")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("<html></html>", response.bodyAsText())
            assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
        }

    @Test
    fun `ユーザー名にドットが入っていても画面が返る`() =
        testApplication {
            putFile("index.html", "<html></html>")
            applicationWith(root)

            // ファイルを引く経路に流れると、拡張子付きの要求と区別が付かず 404 になる
            val response = client.get("/@feed1.example")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("<html></html>", response.bodyAsText())
        }

    @Test
    fun `アカウントの画面のパスで返すindex_htmlもキャッシュされない`() =
        testApplication {
            putFile("index.html", "<html></html>")
            applicationWith(root)

            assertEquals("no-store", client.get("/@feed1").headers[HttpHeaders.CacheControl])
        }

    @Test
    fun `アカウントのパスでもindex_htmlが無ければ404が返る`() =
        testApplication {
            putFile("frontend.js", "console.log()")
            applicationWith(root)

            assertEquals(HttpStatusCode.NotFound, client.get("/@feed1").status)
        }

    @Test
    fun `無いファイルは404が返る`() =
        testApplication {
            putFile("index.html", "<html></html>")
            applicationWith(root)

            assertEquals(HttpStatusCode.NotFound, client.get("/missing.js").status)
        }

    @Test
    fun `エンコードした親ディレクトリでも外のファイルは読めない`() =
        testApplication {
            putFile("index.html", "<html></html>")
            applicationWith(root)

            assertEquals(HttpStatusCode.NotFound, client.get("/%2e%2e/secret.txt").status)
        }

    @Test
    fun `配信先が未設定ならrootは404が返る`() =
        testApplication {
            applicationWith(srcDir = null)

            assertEquals(HttpStatusCode.NotFound, client.get("/").status)
        }

    @Test
    fun `配信先のディレクトリが無ければrootは404が返る`() =
        testApplication {
            applicationWith(root.resolve("not-exists"))

            assertEquals(HttpStatusCode.NotFound, client.get("/").status)
        }

    @Test
    fun `サーバー自身のパスは静的配信より優先される`() =
        testApplication {
            // healthz という名前のファイルを置いても、こちらが勝ってはいけない
            putFile("healthz", "static")
            putFile("index.html", "<html></html>")
            applicationWith(root)

            val response = client.get("/healthz")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"status":"ok"}""", response.bodyAsText())
        }

    private fun ApplicationTestBuilder.applicationWith(srcDir: Path?) {
        val env =
            if (srcDir == null) {
                TestServerEnv.value
            } else {
                TestServerEnv.of("STATIC_SRC_DIR" to srcDir.toString())
            }

        application {
            module(testDependencies(env = env))
        }
    }

    private fun putFile(
        relativePath: String,
        content: String,
    ) {
        val path = root.resolve(relativePath)
        path.parent.createDirectories()
        path.writeText(content)
    }
}
