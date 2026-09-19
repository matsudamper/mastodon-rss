package net.matsudamper.mastodon.rss.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import net.matsudamper.mastodon.rss.FakeFollowerStore
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.TestDelivery
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.TestWebPageUrls
import net.matsudamper.mastodon.rss.note.NotePublisher

class NoteReaderTest {
    private val repositories = FakeRepositories()

    private val notes = RepositoryNoteStore(repositories.notes)

    private val reader = NoteReader(directory = TestLocalActor.directory, notes = notes)

    private fun enqueue(count: Int) {
        val enqueuer = NoteEnqueuer(
            publisher = NotePublisher(notes, TestWebPageUrls),
            followers = repositories.followers,
            deliveryQueue = repositories.deliveryQueue,
        )
        repeat(count) { index ->
            enqueuer.enqueue(sender = TestLocalActor.urls, contentHtml = "<p>本文 $index</p>")
        }
    }

    @Test
    fun `続きがあるかは取れた件数で決まる`() = runBlocking {
        enqueue(3)

        val first = reader.notes(username = TestLocalActor.USERNAME, after = null, limit = 2)
        assertEquals(2, first.notes.size)
        assertEquals(true, first.hasMore)

        val next = reader.notes(
            username = TestLocalActor.USERNAME,
            after = assertNotNull(first.nextPosition),
            limit = 2,
        )
        assertEquals(1, next.notes.size)
        // ちょうど取り切ったので次は無い。総数と突き合わせない
        assertEquals(false, next.hasMore)
        assertNull(next.nextPosition)
    }

    @Test
    fun `要求された件数が上限を超えても上限で切る`() = runBlocking {
        enqueue(2)

        val page = reader.notes(
            username = TestLocalActor.USERNAME,
            after = null,
            limit = NoteReader.MAX_LIST_LIMIT + 1000,
        )

        assertEquals(2, page.notes.size)
    }

    @Test
    fun `知らないアカウントの一覧は空`() = runBlocking {
        enqueue(1)

        val page = reader.notes(username = "nobody", after = null, limit = 10)

        assertEquals(emptyList(), page.notes)
        assertEquals(false, page.hasMore)
    }
}
