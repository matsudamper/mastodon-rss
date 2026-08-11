package net.matsudamper.mastodon.rss.nodeinfo

import kotlin.test.Test
import kotlin.test.assertEquals
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.module
import net.matsudamper.mastodon.rss.testDependencies

// 調査ツール向けの任意実装。無くても Mastodon からのフォローには影響しない。
class NodeInfoRoutesTest {
    private fun ApplicationTestBuilder.installModule() {
        application {
            module(testDependencies())
        }
    }

    @Test
    fun `well-known から 2 の 1 の URL が引ける`() =
        testApplication {
            installModule()

            val response = client.get("/.well-known/nodeinfo")

            assertEquals(HttpStatusCode.OK, response.status)

            val body = AppJson.decodeFromString(NodeInfoDiscovery.serializer(), response.bodyAsText())
            val link = body.links.single()
            assertEquals("http://nodeinfo.diaspora.software/ns/schema/2.1", link.rel)
            assertEquals("https://example.com/nodeinfo/2.1", link.href)
        }

    @Test
    fun `2 の 1 は software と protocols を返す`() =
        testApplication {
            installModule()

            val response = client.get("/nodeinfo/2.1")

            assertEquals(HttpStatusCode.OK, response.status)

            val body = AppJson.decodeFromString(NodeInfo.serializer(), response.bodyAsText())
            assertEquals("2.1", body.version)
            assertEquals("mastodon-rss", body.software.name)
            assertEquals("https://github.com/matsudamper/mastodon-rss", body.software.repository)
            assertEquals(listOf("activitypub"), body.protocols)
            assertEquals(false, body.openRegistrations)
            assertEquals(1, body.usage.users.total)
        }
}
