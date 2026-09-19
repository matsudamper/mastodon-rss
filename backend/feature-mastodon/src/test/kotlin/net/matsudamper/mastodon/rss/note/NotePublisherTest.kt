package net.matsudamper.mastodon.rss.note

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.mastodon.rss.FakeFollowerStore
import net.matsudamper.mastodon.rss.FakeNoteStore
import net.matsudamper.mastodon.rss.TestDelivery
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.TestWebPageUrls
import net.matsudamper.mastodon.rss.activity.ActivityStreamsIri
import net.matsudamper.mastodon.rss.activity.CreateNoteActivity
import net.matsudamper.mastodon.rss.json.AppJson

// フォロワーに送る Create{Note} を組み立てるところ。
// 記録も配信もここではしない。組み立てた JSON が Mastodon に通る形かどうかが肝。
class NotePublisherTest {
    private val sender = TestLocalActor.urls

    private fun publisher(): NotePublisher = NotePublisher(FakeNoteStore(), FakeFollowerStore(), TestDelivery(), TestWebPageUrls)

    @Test
    fun `Create に包んだ Note を組み立てる`() {
        val prepared = publisher().prepare(sender, "<p>こんにちは</p>")

        val activity = AppJson.decodeFromString(CreateNoteActivity.serializer(), prepared.activityJson)
        assertEquals("Create", activity.type)
        assertEquals(sender.actorId, activity.actor)
        assertEquals(listOf(ActivityStreamsIri.PUBLIC_AUDIENCE), activity.to)
        assertEquals(listOf(sender.followers), activity.cc)

        assertEquals("Note", activity.target.type)
        assertEquals("<p>こんにちは</p>", activity.target.content)
        assertEquals(sender.actorId, activity.target.attributedTo)
        assertEquals("https://example.com/notes/${prepared.publicId.value}", activity.target.id.value)
        // 相手がパーマリンクとして開くのは画面の URL
        assertEquals("https://example.com/@${TestLocalActor.USERNAME}/${prepared.publicId.value}", activity.target.url)
        // 外側の Create が @context を持つので、中で重ねない
        assertNull(activity.target.context)
    }

    @Test
    fun `id は UUID v7 で時刻と揃う`() {
        val prepared = publisher().prepare(sender, "<p>本文</p>")

        assertEquals(7, UUID.fromString(prepared.publicId.value).version())
        assertEquals("<p>本文</p>", prepared.contentHtml)
        assertEquals(prepared.publishedAt.toActivityPubPublished(), AppJson.decodeFromString(CreateNoteActivity.serializer(), prepared.activityJson).published)
    }

    @Test
    fun `組み立てるだけで記録も配信もしない`() {
        val notes = FakeNoteStore()
        val delivery = TestDelivery()
        val publisher = NotePublisher(notes, FakeFollowerStore(), delivery, TestWebPageUrls)

        publisher.prepare(sender, "<p>本文</p>")

        assertTrue(notes.added.isEmpty())
        assertTrue(delivery.delivered.isEmpty())
    }

    @Test
    fun `呼ぶたびに別の id になる`() {
        val publisher = publisher()

        val first = publisher.prepare(sender, "<p>1</p>")
        val second = publisher.prepare(sender, "<p>2</p>")

        assertTrue(first.publicId != second.publicId)
    }
}
