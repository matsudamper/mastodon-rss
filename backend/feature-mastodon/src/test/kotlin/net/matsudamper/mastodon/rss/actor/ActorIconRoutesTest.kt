package net.matsudamper.mastodon.rss.actor

import kotlin.test.Test
import kotlin.test.assertEquals
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.matsudamper.mastodon.rss.TestLocalActor

// Actor JSON の icon が指す先。Mastodon と公開画面の両方がここを引く。
class ActorIconRoutesTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    private fun ApplicationTestBuilder.installModule(icons: ActorIcons) {
        application {
            routing {
                actorIconRoutes(TestLocalActor.directory, icons)
            }
        }
    }

    @Test
    fun `アイコンがあれば取得元の種類のまま返る`() =
        testApplication {
            installModule(
                object : ActorIcons {
                    override suspend fun find(username: String): ActorIcon =
                        ActorIcon(bytes = png, contentType = ContentType.Image.PNG)
                },
            )

            val response = client.get("/users/admin/icon")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Image.PNG, response.contentType()?.withoutParameters())
            assertEquals(png.toList(), response.readRawBytes().toList())
            assertEquals("public, max-age=3600", response.headers[HttpHeaders.CacheControl])
            // 画像として受けたものを別の種類として解釈されないようにする
            assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        }

    @Test
    fun `アイコンが無いアカウントは 404`() =
        testApplication {
            installModule(
                object : ActorIcons {
                    override suspend fun find(username: String): ActorIcon? = null
                },
            )

            assertEquals(HttpStatusCode.NotFound, client.get("/users/admin/icon").status)
        }

    @Test
    fun `知らない名前は 404`() =
        testApplication {
            installModule(
                object : ActorIcons {
                    override suspend fun find(username: String): ActorIcon =
                        ActorIcon(bytes = png, contentType = ContentType.Image.PNG)
                },
            )

            assertEquals(HttpStatusCode.NotFound, client.get("/users/other/icon").status)
        }
}
