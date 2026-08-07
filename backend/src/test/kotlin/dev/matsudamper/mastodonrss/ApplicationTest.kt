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
            module()
        }

        val response = client.get("/healthz")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"ok"}""", response.bodyAsText())
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
    }

    @Test
    fun `activity+jsonをAcceptすると同じContent-Typeで返る`() = testApplication {
        application {
            module()
        }

        val response = client.get("/healthz") {
            accept(ActivityPubContentTypes.ActivityJson)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ActivityPubContentTypes.ActivityJson, response.contentType()?.withoutParameters())
    }
}
