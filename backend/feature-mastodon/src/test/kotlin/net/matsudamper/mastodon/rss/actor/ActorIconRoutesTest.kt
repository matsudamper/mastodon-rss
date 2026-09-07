package net.matsudamper.mastodon.rss.actor

import java.time.Duration
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
                        ActorIcon(bytes = png, contentType = ContentType.Image.PNG, version = VERSION, cacheFor = Duration.ofSeconds(60))
                },
            )

            val response = client.get("/users/admin/icon")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Image.PNG, response.contentType()?.withoutParameters())
            assertEquals(png.toList(), response.readRawBytes().toList())
            assertEquals("public, max-age=60", response.headers[HttpHeaders.CacheControl])
            assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        }

    @Test
    fun `取得元から決まる値が一致する URL は長く持たせる`() =
        testApplication {
            installModule(
                object : ActorIcons {
                    override suspend fun find(username: String): ActorIcon =
                        ActorIcon(bytes = png, contentType = ContentType.Image.PNG, version = VERSION, cacheFor = Duration.ofSeconds(60))
                },
            )

            val response = client.get("/users/admin/icon?v=$VERSION")

            assertEquals("public, max-age=31536000, immutable", response.headers[HttpHeaders.CacheControl])
        }

    @Test
    fun `取得元から決まる値が一致しない URL は配信元の言い分に従う`() =
        testApplication {
            installModule(
                object : ActorIcons {
                    override suspend fun find(username: String): ActorIcon =
                        ActorIcon(bytes = png, contentType = ContentType.Image.PNG, version = VERSION, cacheFor = Duration.ofSeconds(60))
                },
            )

            val response = client.get("/users/admin/icon?v=other")

            assertEquals("public, max-age=60", response.headers[HttpHeaders.CacheControl])
        }

    @Test
    fun `持たせる時間が無いアイコンは持たせない`() =
        testApplication {
            installModule(
                object : ActorIcons {
                    override suspend fun find(username: String): ActorIcon =
                        ActorIcon(bytes = png, contentType = ContentType.Image.PNG, version = VERSION, cacheFor = Duration.ZERO)
                },
            )

            val response = client.get("/users/admin/icon")

            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        }

    @Test
    fun `アイコンが無いアカウントは 404 で、その応答は持たせない`() =
        testApplication {
            installModule(
                object : ActorIcons {
                    override suspend fun find(username: String): ActorIcon? = null
                },
            )

            val response = client.get("/users/admin/icon")

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        }

    @Test
    fun `知らない名前は 404 で、その応答も持たせない`() =
        testApplication {
            installModule(
                object : ActorIcons {
                    override suspend fun find(username: String): ActorIcon =
                        ActorIcon(bytes = png, contentType = ContentType.Image.PNG, version = VERSION, cacheFor = Duration.ofSeconds(60))
                },
            )

            val response = client.get("/users/other/icon")

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        }

    private companion object {
        const val VERSION = "abc123"
    }
}
