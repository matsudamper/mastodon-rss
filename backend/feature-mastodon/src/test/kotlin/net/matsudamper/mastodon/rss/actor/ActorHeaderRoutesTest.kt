package net.matsudamper.mastodon.rss.actor

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import net.matsudamper.mastodon.rss.TestLocalActor

class ActorHeaderRoutesTest {
    @Test
    fun `置いてあるヘッダーを返す`() =
        testApplication {
            application {
                routing {
                    actorHeaderRoutes(
                        directory = TestLocalActor.directory,
                        headers = object : ActorHeaders {
                            override suspend fun find(username: String): ActorHeader? = ActorHeader(
                                bytes = "header".encodeToByteArray(),
                                contentType = ContentType.Image.JPEG,
                                version = "abc",
                                cacheFor = Duration.ofSeconds(60),
                            )
                        },
                    )
                }
            }

            val response = client.get("/users/${TestLocalActor.STORED_USERNAME}/header?v=abc")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("header", response.bodyAsText())
            assertEquals("public, max-age=31536000, immutable", response.headers[HttpHeaders.CacheControl])
            assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        }

    @Test
    fun `期限切れヘッダーは版が一致してもキャッシュさせない`() =
        testApplication {
            application {
                routing {
                    actorHeaderRoutes(
                        directory = TestLocalActor.directory,
                        headers = object : ActorHeaders {
                            override suspend fun find(username: String): ActorHeader? = ActorHeader(
                                bytes = "header".encodeToByteArray(),
                                contentType = ContentType.Image.JPEG,
                                version = "abc",
                                cacheFor = Duration.ZERO,
                            )
                        },
                    )
                }
            }

            val response = client.get("/users/${TestLocalActor.STORED_USERNAME}/header?v=abc")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        }

    @Test
    fun `ヘッダーが無ければキャッシュさせず404を返す`() =
        testApplication {
            application {
                routing {
                    actorHeaderRoutes(
                        directory = TestLocalActor.directory,
                        headers = object : ActorHeaders {
                            override suspend fun find(username: String): ActorHeader? = null
                        },
                    )
                }
            }

            val response = client.get("/users/${TestLocalActor.STORED_USERNAME}/header")

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        }
}
