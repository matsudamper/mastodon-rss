package net.matsudamper.mastodon.rss.staticfiles

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.TestActorKey
import net.matsudamper.mastodon.rss.TestPublicKeys
import net.matsudamper.mastodon.rss.TestServerEnv
import net.matsudamper.mastodon.rss.module
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
            module(FakeRepositories(), TestActorKey.value, env, TestPublicKeys())
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
