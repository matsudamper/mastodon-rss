package dev.matsudamper.mastodonrss.webfinger

import dev.matsudamper.mastodonrss.FakeRepositories
import dev.matsudamper.mastodonrss.TestActorKey
import dev.matsudamper.mastodonrss.TestServerConfig
import dev.matsudamper.mastodonrss.json.AppJson
import dev.matsudamper.mastodonrss.module
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// アカウント発見の 1 ホップ目。ここが 404 だと Mastodon の検索に何も出ない。
class WebFingerRoutesTest {
    private fun ApplicationTestBuilder.installModule() {
        application {
            module(FakeRepositories(), TestActorKey.value, TestServerConfig.value)
        }
    }

    @Test
    fun `acct を引くと self リンクに Actor の URL が返る`() =
        testApplication {
            installModule()

            val response = client.get("/.well-known/webfinger?resource=acct:admin@example.com")

            assertEquals(HttpStatusCode.OK, response.status)
            // application/json で返すと WebFinger として扱わない実装がある
            assertEquals("application/jrd+json", response.contentType()?.withoutParameters()?.toString())

            val body = AppJson.decodeFromString(WebFingerResponse.serializer(), response.bodyAsText())
            assertEquals("acct:admin@example.com", body.subject)
            assertEquals(listOf("https://example.com/users/admin"), body.aliases)

            val self = body.links.single { it.rel == "self" }
            assertEquals("application/activity+json", self.type)
            assertEquals("https://example.com/users/admin", self.href)
        }

    @Test
    fun `大文字小文字が違っても引ける`() =
        testApplication {
            installModule()

            val response = client.get("/.well-known/webfinger?resource=acct:Admin@Example.com")

            assertEquals(HttpStatusCode.OK, response.status)
            // 要求された綴りではなく正規の acct を返す
            val body = AppJson.decodeFromString(WebFingerResponse.serializer(), response.bodyAsText())
            assertEquals("acct:admin@example.com", body.subject)
        }

    @Test
    fun `知らない resource は404`() =
        testApplication {
            installModule()

            val response = client.get("/.well-known/webfinger?resource=acct:other@example.com")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `resource が無ければ400`() =
        testApplication {
            installModule()

            assertEquals(HttpStatusCode.BadRequest, client.get("/.well-known/webfinger").status)
            assertEquals(HttpStatusCode.BadRequest, client.get("/.well-known/webfinger?resource=").status)
        }

    @Test
    fun `null のフィールドはキーごと出さない`() =
        testApplication {
            installModule()

            val response = client.get("/.well-known/webfinger?resource=acct:admin@example.com")
            val body = AppJson.decodeFromString(WebFingerResponse.serializer(), response.bodyAsText())

            // href/type を持たない link は無いので、"type":null のような出力にならないことを見る
            assertNull(body.links.firstOrNull { it.href == null })
        }
}
