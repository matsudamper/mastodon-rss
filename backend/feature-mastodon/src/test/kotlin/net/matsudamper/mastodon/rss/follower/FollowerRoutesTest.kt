package net.matsudamper.mastodon.rss.follower

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.builtins.serializer
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import net.matsudamper.mastodon.rss.FakeFollowerStore
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.actor.RemoteActor
import net.matsudamper.mastodon.rss.collection.COLLECTION_PAGE_SIZE
import net.matsudamper.mastodon.rss.collection.OrderedCollection
import net.matsudamper.mastodon.rss.collection.OrderedCollectionPage
import net.matsudamper.mastodon.rss.json.AppJson

class FollowerRoutesTest {
    @Test
    fun `Mastodon が Actor から辿れるフォロワーコレクションを返す`() =
        testApplication {
            val followers = followerStore(2)
            application {
                routing {
                    followerRoutes(TestLocalActor.directory, followers)
                }
            }

            val collectionResponse = client.get("/users/admin/followers")
            assertEquals(HttpStatusCode.OK, collectionResponse.status)
            assertEquals("application/activity+json", collectionResponse.contentType()?.withoutParameters()?.toString())

            val collection = AppJson.decodeFromString(OrderedCollection.serializer(), collectionResponse.bodyAsText())
            assertEquals("https://example.com/users/admin/followers", collection.id)
            assertEquals(2, collection.totalItems)
            assertEquals("https://example.com/users/admin/followers?cursor=", collection.first)

            val pageResponse = client.get("/users/admin/followers?cursor=")
            val page = AppJson.decodeFromString(
                OrderedCollectionPage.serializer(String.serializer()),
                pageResponse.bodyAsText(),
            )
            assertEquals(collection.id, page.partOf)
            assertEquals(
                listOf("https://remote.example/users/0", "https://remote.example/users/1"),
                page.orderedItems,
            )
            assertNull(page.next)
        }

    @Test
    fun `ページと同数のフォロワーなら次ページを読み込める`() =
        testApplication {
            val followers = followerStore(COLLECTION_PAGE_SIZE)
            application {
                routing {
                    followerRoutes(TestLocalActor.directory, followers)
                }
            }

            val response = client.get("/users/admin/followers?cursor=")
            val page = AppJson.decodeFromString(
                OrderedCollectionPage.serializer(String.serializer()),
                response.bodyAsText(),
            )

            assertEquals(COLLECTION_PAGE_SIZE, page.orderedItems.size)
            val next = requireNotNull(page.next)
            val nextResponse = client.get(next.substringAfter("https://example.com"))
            val nextPage = AppJson.decodeFromString(
                OrderedCollectionPage.serializer(String.serializer()),
                nextResponse.bodyAsText(),
            )
            assertEquals(emptyList(), nextPage.orderedItems)
            assertNull(nextPage.next)
        }

    @Test
    fun `ページより多いフォロワーなら次ページを返す`() =
        testApplication {
            val followers = followerStore(COLLECTION_PAGE_SIZE + 1)
            application {
                routing {
                    followerRoutes(TestLocalActor.directory, followers)
                }
            }

            val response = client.get("/users/admin/followers?cursor=")
            val page = AppJson.decodeFromString(
                OrderedCollectionPage.serializer(String.serializer()),
                response.bodyAsText(),
            )

            assertEquals(COLLECTION_PAGE_SIZE, page.orderedItems.size)
            assertEquals(
                "https://example.com/users/admin/followers?cursor=https%3A%2F%2Fremote.example%2Fusers%2F8",
                page.next,
            )
        }

    private fun followerStore(count: Int): FakeFollowerStore =
        FakeFollowerStore().apply {
            repeat(count) { index ->
                val actorId = "https://remote.example/users/$index"
                record(
                    username = TestLocalActor.USERNAME,
                    follower = RemoteActor(
                        actorId = actorId,
                        inbox = "$actorId/inbox",
                        sharedInbox = null,
                        publicKeyPem = "public-key",
                    ),
                    followActivityUri = "https://remote.example/activities/$index",
                    receivedAt = Instant.EPOCH,
                )
                markAccepted(TestLocalActor.USERNAME, actorId, Instant.EPOCH)
            }
        }
}
