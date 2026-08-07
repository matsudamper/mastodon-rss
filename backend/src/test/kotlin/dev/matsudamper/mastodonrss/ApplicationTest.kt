package dev.matsudamper.mastodonrss

import dev.matsudamper.mastodonrss.activitypub.ActivityPubContentTypes
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun `healthzにアクセスすると200とstatus okのJSONが返る`() = testApplication {
        application {
            module(FakeRepositories())
        }

        val response = client.get("/healthz")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"ok"}""", response.bodyAsText())
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
    }

    @Test
    fun `activity+jsonをAcceptすると同じContent-Typeで返る`() = testApplication {
        application {
            module(FakeRepositories())
        }

        val response = client.get("/healthz") {
            accept(ActivityPubContentTypes.ActivityJson)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ActivityPubContentTypes.ActivityJson, response.contentType()?.withoutParameters())
    }

    @Test
    fun `起動時にDBの書き込み確認が走る`() = testApplication {
        val repositories = FakeRepositories()
        application {
            module(repositories)
        }

        // testApplication は最初のリクエストまでアプリケーションを起動しない
        client.get("/healthz")

        assertEquals(1, repositories.verifyWritableCallCount)
    }
}
