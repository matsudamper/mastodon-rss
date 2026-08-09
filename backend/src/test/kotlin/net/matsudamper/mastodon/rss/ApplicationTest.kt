package net.matsudamper.mastodon.rss

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

// ルーティングとレスポンスの形を確認する。
// DB そのものの挙動は :backend:repository 側でテストするので、
// ここでは FakeRepositories を渡して実ファイルを触らない。
class ApplicationTest {
    @Test
    fun `healthzにアクセスすると200とstatus okのJSONが返る`() =
        testApplication {
            application {
                module(FakeRepositories(), TestActorKey.value, TestServerConfig.value, TestStaticFilesConfig.value)
            }

            val response = client.get("/healthz")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"status":"ok"}""", response.bodyAsText())
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        }

    @Test
    fun `起動時にDBの書き込み確認が走る`() =
        testApplication {
            val repositories = FakeRepositories()
            application {
                module(repositories, TestActorKey.value, TestServerConfig.value, TestStaticFilesConfig.value)
            }

            // testApplication は最初のリクエストまでアプリケーションを起動しない
            client.get("/healthz")

            assertEquals(1, repositories.verifyWritableCallCount)
        }
}
