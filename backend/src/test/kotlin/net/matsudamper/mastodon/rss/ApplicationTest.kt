package net.matsudamper.mastodon.rss

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication

// ルーティングとレスポンスの形を確認する。
// DB そのものの挙動は :backend:repository 側でテストするので、
// ここでは FakeRepositories を渡して実ファイルを触らない。
class ApplicationTest {
    @Test
    fun `リンクプレビュー画像は認証情報の有無によらず共有キャッシュ可能`() =
        testApplication {
            application {
                module(testDependencies())
            }

            val url = "/link-previews/image.bin?note=missing&link=0&v=0"
            val anonymous = client.get(url)
            val authenticated = client.get(url) {
                header(HttpHeaders.Cookie, "admin_session=invalid")
                header(HttpHeaders.Authorization, "Bearer invalid")
            }

            for (response in listOf(anonymous, authenticated)) {
                assertEquals(HttpStatusCode.NotFound, response.status)
                assertEquals("public, max-age=600", response.headers[HttpHeaders.CacheControl])
                assertNull(response.headers[HttpHeaders.SetCookie])
                assertNull(response.headers[HttpHeaders.Vary])
            }
        }

    @Test
    fun `healthzにアクセスすると200とstatus okのJSONが返る`() =
        testApplication {
            application {
                module(testDependencies())
            }

            val response = client.get("/healthz")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"status":"ok"}""", response.bodyAsText())
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        }

    @Test
    fun `ActivityPub のエンドポイントが組み込まれている`() =
        testApplication {
            val repositories = FakeRepositories()
            repositories.accounts.add(username = TestServerEnv.USERNAME, createdAt = Instant.now())
            application {
                module(testDependencies(repositories = repositories))
            }

            // 返す中身は kotpub の core 側で確かめる。
            // ここで見るのは module() がそれらを組み込んでいることだけ
            assertEquals(
                HttpStatusCode.OK,
                client.get("/.well-known/webfinger?resource=acct:admin@example.com").status,
            )
            val actorResponse = client.get("/users/admin") {
                header(HttpHeaders.Accept, "application/activity+json")
            }
            assertEquals(HttpStatusCode.OK, actorResponse.status)
            assertEquals(ContentType.parse("application/activity+json"), actorResponse.contentType()?.withoutParameters())
            assertContains(actorResponse.bodyAsText(), "\"id\":\"https://example.com/users/admin\"")

            val noRedirect = createClient { followRedirects = false }
            val htmlResponse = noRedirect.get("/users/admin")
            assertEquals(HttpStatusCode.MovedPermanently, htmlResponse.status)
            assertEquals("/@admin", htmlResponse.headers[HttpHeaders.Location])
            assertEquals("Origin, Accept", htmlResponse.headers[HttpHeaders.Vary])
            assertEquals(HttpStatusCode.OK, client.get("/.well-known/nodeinfo").status)

            // 署名が無いので inbox は 401。ルートが無ければ 404 になる
            assertEquals(HttpStatusCode.Unauthorized, client.post("/users/admin/inbox").status)
        }

    @Test
    fun `起動時にDBの書き込み確認が走る`() =
        testApplication {
            val repositories = FakeRepositories()
            application {
                module(testDependencies(repositories = repositories))
            }

            // testApplication は最初のリクエストまでアプリケーションを起動しない
            client.get("/healthz")

            assertEquals(1, repositories.verifyWritableCallCount)
        }
}
