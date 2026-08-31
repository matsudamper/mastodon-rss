package net.matsudamper.mastodon.rss.note

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.matsudamper.mastodon.rss.FakeNoteStore
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.activity.ActivityStreamsIri
import net.matsudamper.mastodon.rss.activity.CreateNoteActivity
import net.matsudamper.mastodon.rss.collection.COLLECTION_CURSOR_PARAM
import net.matsudamper.mastodon.rss.collection.COLLECTION_PAGE_SIZE
import net.matsudamper.mastodon.rss.collection.OrderedCollection
import net.matsudamper.mastodon.rss.collection.OrderedCollectionPage
import net.matsudamper.mastodon.rss.collection.OrderedCollectionWithItems
import net.matsudamper.mastodon.rss.entity.PublicNoteId
import net.matsudamper.mastodon.rss.json.AppJson

// Mastodon が後から引きに来る投稿のパーマリンクと outbox。
// タイムラインに出ていても、ここが 404 だと開けない投稿になる。
class NoteRoutesTest {
    private val publishedAt = Instant.parse("2026-08-10T00:00:00Z")

    private fun ApplicationTestBuilder.installModule(notes: FakeNoteStore = FakeNoteStore()) {
        application {
            routing {
                noteRoutes(TestLocalActor.DOMAIN, notes)
                outboxRoutes(TestLocalActor.directory, notes)
                featuredRoutes(TestLocalActor.directory)
            }
        }
    }

    private fun note(
        publicId: String,
        username: String = TestLocalActor.USERNAME,
        publishedAt: Instant = this.publishedAt,
        contentHtml: String = "<p>$publicId</p>",
    ): StoredNote = StoredNote(
        publicId = PublicNoteId(publicId),
        username = username,
        contentHtml = contentHtml,
        publishedAt = publishedAt,
    )

    @Test
    fun `投稿のパーマリンクが返る`() =
        testApplication {
            val notes = FakeNoteStore()
            notes.add(note("abc"))
            installModule(notes)

            val response = client.get("/notes/abc")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("application/activity+json", response.contentType()?.withoutParameters()?.toString())

            val body = AppJson.decodeFromString(Note.serializer(), response.bodyAsText())
            assertEquals("https://example.com/notes/abc", body.id.value)
            assertEquals("Note", body.type)
            assertEquals("https://example.com/users/admin", body.attributedTo)
            assertEquals("<p>abc</p>", body.content)
            assertEquals(publishedAt.toActivityPubPublished(), body.published)
            assertEquals("https://example.com/notes/abc", body.atomUri)
            assertEquals(listOf(ActivityStreamsIri.PUBLIC_AUDIENCE), body.to)
            assertEquals(listOf("https://example.com/users/admin/followers"), body.cc)
            assertEquals("https://example.com/notes/abc", body.url)
            assertEquals(listOf("https://www.w3.org/ns/activitystreams"), body.context)
        }

    @Test
    fun `知らない投稿 id は404`() =
        testApplication {
            installModule()

            assertEquals(HttpStatusCode.NotFound, client.get("/notes/missing").status)
        }

    @Test
    fun `outbox は cursor が無ければ総数と入口だけ返す`() =
        testApplication {
            val notes = FakeNoteStore()
            notes.add(note("note-1"))
            installModule(notes)

            val response = client.get("/users/admin/outbox")

            assertEquals(HttpStatusCode.OK, response.status)

            val collection = AppJson.decodeFromString(OrderedCollection.serializer(), response.bodyAsText())
            assertEquals("https://example.com/users/admin/outbox", collection.id)
            assertEquals("OrderedCollection", collection.type)
            assertEquals(1, collection.totalItems)
            assertEquals("https://example.com/users/admin/outbox?$COLLECTION_CURSOR_PARAM=", collection.first)
        }

    @Test
    fun `outbox の先頭ページは Create が並ぶ`() =
        testApplication {
            val notes = FakeNoteStore()
            notes.add(note("note-1"))
            installModule(notes)

            val response = client.get("/users/admin/outbox?$COLLECTION_CURSOR_PARAM=")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("application/activity+json", response.contentType()?.withoutParameters()?.toString())

            val page = AppJson.decodeFromString(
                OrderedCollectionPage.serializer(CreateNoteActivity.serializer()),
                response.bodyAsText(),
            )
            assertEquals("OrderedCollectionPage", page.type)
            assertEquals("https://example.com/users/admin/outbox", page.partOf)
            assertEquals(1, page.totalItems)
            assertEquals(1, page.orderedItems.size)
            assertNull(page.next)

            val create = page.orderedItems.single()
            assertEquals("Create", create.type)
            assertEquals("https://example.com/notes/note-1#create", create.id.value)
            assertEquals("https://example.com/users/admin", create.actor)
            assertEquals(listOf(ActivityStreamsIri.PUBLIC_AUDIENCE), create.to)
            assertEquals(listOf("https://example.com/users/admin/followers"), create.cc)
            assertNull(create.target.context)
            assertEquals("https://example.com/notes/note-1", create.target.id.value)
            assertEquals("<p>note-1</p>", create.target.content)
        }

    @Test
    fun `outbox は cursor で続きを返す`() =
        testApplication {
            val notes = FakeNoteStore()
            repeat(COLLECTION_PAGE_SIZE + 1) { index ->
                notes.add(
                    note(
                        publicId = "note-$index",
                        publishedAt = publishedAt.plusSeconds(index.toLong()),
                    ),
                )
            }
            installModule(notes)

            val first = client.get("/users/admin/outbox?$COLLECTION_CURSOR_PARAM=").bodyAsText()
            val firstPage = AppJson.decodeFromString(
                OrderedCollectionPage.serializer(CreateNoteActivity.serializer()),
                first,
            )
            assertEquals(COLLECTION_PAGE_SIZE, firstPage.orderedItems.size)
            assertEquals("note-1", firstPage.orderedItems.last().target.id.value.removePrefix("https://example.com/notes/"))

            val nextUrl = firstPage.next
            assertTrue(nextUrl != null)

            val second = client.get(nextUrl.substringAfter("example.com")).bodyAsText()
            val secondPage = AppJson.decodeFromString(
                OrderedCollectionPage.serializer(CreateNoteActivity.serializer()),
                second,
            )
            assertEquals(1, secondPage.orderedItems.size)
            assertEquals("note-0", secondPage.orderedItems.single().target.id.value.removePrefix("https://example.com/notes/"))
            assertNull(secondPage.next)
        }

    @Test
    fun `知らないユーザー名の outbox は404`() =
        testApplication {
            installModule()

            assertEquals(HttpStatusCode.NotFound, client.get("/users/other/outbox").status)
        }

    @Test
    fun `featured は空のコレクションを返す`() =
        testApplication {
            val notes = FakeNoteStore()
            notes.add(note("note-1"))
            installModule(notes)

            val response = client.get("/users/admin/collections/featured")

            assertEquals(HttpStatusCode.OK, response.status)

            val collection = AppJson.decodeFromString(
                OrderedCollectionWithItems.serializer(Note.serializer()),
                response.bodyAsText(),
            )
            assertEquals("https://example.com/users/admin/collections/featured", collection.id)
            assertEquals("OrderedCollection", collection.type)
            assertEquals(0, collection.totalItems)
            assertEquals(emptyList(), collection.orderedItems)
        }

    @Test
    fun `知らないユーザー名の featured は404`() =
        testApplication {
            installModule()

            assertEquals(HttpStatusCode.NotFound, client.get("/users/other/collections/featured").status)
        }

    @Test
    fun `Accept が ld+json ならその Content-Type で返す`() =
        testApplication {
            val notes = FakeNoteStore()
            notes.add(note("abc"))
            installModule(notes)

            val accept = """application/ld+json; profile="https://www.w3.org/ns/activitystreams""""
            val response = client.get("/notes/abc") { header(HttpHeaders.Accept, accept) }

            assertEquals("application/ld+json", response.contentType()?.withoutParameters()?.toString())
        }
}
