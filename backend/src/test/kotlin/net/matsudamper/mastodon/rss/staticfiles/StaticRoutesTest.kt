package net.matsudamper.mastodon.rss.staticfiles

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.matsudamper.kotpub.activitypub.Actor
import net.matsudamper.kotpub.json.AppJson
import net.matsudamper.kotpub.webfinger.WebFingerResponse
import net.matsudamper.mastodon.rss.FakeRepositories
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
    fun `プロフィールURLからアクターを発見してWebFingerで確認できる`() =
        testApplication {
            putFile("index.html", "<html></html>")
            applicationWith(root)

            val actorUrl = "https://example.com/users/feed1"
            val accept = """application/ld+json; profile="https://www.w3.org/ns/activitystreams", application/activity+json, text/html;q=0.1"""
            listOf("/@feed1", "/@feed1/").forEach { path ->
                val response = client.get(path) { header(HttpHeaders.Accept, accept) }
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("application/activity+json", response.contentType()?.withoutParameters()?.toString())
                assertEquals(actorUrl, AppJson.decodeFromString(Actor.serializer(), response.bodyAsText()).id)
                assertEquals("Accept", response.headers[HttpHeaders.Vary])
                assertEquals(null, response.headers[HttpHeaders.Link])
            }

            val actorResponse = client.get("/users/feed1") { header(HttpHeaders.Accept, accept) }
            assertEquals(HttpStatusCode.OK, actorResponse.status)
            val actor = AppJson.decodeFromString(Actor.serializer(), actorResponse.bodyAsText())
            assertEquals(actorUrl, actor.id)
            assertEquals(actorUrl, actor.publicKey.owner)
            val webFingerResponse = client.get("/.well-known/webfinger?resource=acct:${actor.preferredUsername}@example.com")
            val webFinger = AppJson.decodeFromString(WebFingerResponse.serializer(), webFingerResponse.bodyAsText())
            assertEquals(actor.id, webFinger.links.single { it.rel == "self" }.href)
        }

    @Test
    fun `プロフィールのHTMLには発見リンクがありJSON用URLは301で画面へ戻す`() =
        testApplication {
            putFile("index.html", "<html></html>")
            applicationWith(root)
            val noRedirect = createClient { followRedirects = false }

            listOf("text/html", "*/*", "text/html, application/activity+json;q=0.1").forEach { accept ->
                val page = client.get("/@feed1") { header(HttpHeaders.Accept, accept) }
                assertEquals(HttpStatusCode.OK, page.status)
                assertEquals(ContentType.Text.Html, page.contentType()?.withoutParameters())
                assertEquals("Accept", page.headers[HttpHeaders.Vary])
                assertEquals(
                    "<https://example.com/.well-known/webfinger?resource=acct:feed1@example.com>; rel=\"lrdd\"; type=\"application/jrd+json\", " +
                        "<https://example.com/users/feed1>; rel=\"alternate\"; type=\"application/activity+json\"",
                    page.headers[HttpHeaders.Link],
                )
                val redirect = noRedirect.get("/users/feed1") { header(HttpHeaders.Accept, accept) }
                assertEquals(HttpStatusCode.MovedPermanently, redirect.status)
                assertEquals("/@feed1", redirect.headers[HttpHeaders.Location])
                assertEquals("Origin, Accept", redirect.headers[HttpHeaders.Vary])
                assertEquals(null, redirect.headers[HttpHeaders.Link])
            }
        }

    @Test
    fun `静的ファイルがなくてもプロフィールのJSONを返し不明なアカウントは404`() =
        testApplication {
            applicationWith(srcDir = null)
            val accepts = listOf(
                "application/activity+json",
                "application/ld+json",
                "application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\"",
            )
            accepts.forEach { accept ->
                listOf("/@FEED1", "/@feed1/", "/users/feed1").forEach { path ->
                    val response = client.get(path) { header(HttpHeaders.Accept, accept) }
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals("application/activity+json", response.contentType()?.withoutParameters()?.toString())
                    assertEquals("https://example.com/users/feed1", AppJson.decodeFromString(Actor.serializer(), response.bodyAsText()).id)
                }
                listOf("/@missing", "/users/missing").forEach { path ->
                    assertEquals(HttpStatusCode.NotFound, client.get(path) { header(HttpHeaders.Accept, accept) }.status)
                }
            }
        }

    @Test
    fun `存在しないアカウントや管理画面にはアクターのリンクを出さない`() =
        testApplication {
            putFile("index.html", "<html></html>")
            applicationWith(root)

            listOf("/@missing", "/@missing/", "/admin/accounts", "/").forEach { path ->
                assertEquals(null, client.get(path).headers[HttpHeaders.Link])
            }
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
    fun `末尾にスラッシュが付いていても画面が返る`() =
        testApplication {
            putFile("index.html", "<html></html>")
            applicationWith(root)

            val response = client.get("/@feed1.example/")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("<html></html>", response.bodyAsText())
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

        val repositories = FakeRepositories()
        repositories.accounts.add(username = "feed1", createdAt = Instant.now())
        repositories.accounts.add(username = "feed1.example", createdAt = Instant.now())
        application {
            module(testDependencies(env = env, repositories = repositories))
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
