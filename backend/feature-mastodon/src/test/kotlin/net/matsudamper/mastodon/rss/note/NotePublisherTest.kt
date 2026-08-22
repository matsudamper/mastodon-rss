package net.matsudamper.mastodon.rss.note

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import net.matsudamper.mastodon.rss.FakeFollowerStore
import net.matsudamper.mastodon.rss.FakeNoteStore
import net.matsudamper.mastodon.rss.TestDelivery
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.actor.RemoteActor
import net.matsudamper.mastodon.rss.delivery.DeliveryResult
import net.matsudamper.mastodon.rss.json.AppJson

// 投稿を作って全フォロワーに配るところ。
// 記録が先で配信が後、という順番と、sharedInbox でまとめるところが肝。
class NotePublisherTest {
    private val sender = TestLocalActor.urls

    private val now = Instant.parse("2026-08-10T00:00:00Z")

    private fun followers(vararg targets: Pair<String, String?>): FakeFollowerStore = FakeFollowerStore().apply {
        targets.forEachIndexed { index, (actorUri, sharedInbox) ->
            record(
                username = TestLocalActor.USERNAME,
                follower = RemoteActor(
                    actorId = actorUri,
                    inbox = "$actorUri/inbox",
                    sharedInbox = sharedInbox,
                    publicKeyPem = "pem",
                ),
                followActivityUri = "https://remote.example/activities/$index",
                receivedAt = now,
            )
            markAccepted(TestLocalActor.USERNAME, actorUri, now)
        }
    }

    @Test
    fun `フォロワー全員に Create を配る`() = runBlocking {
        val delivery = TestDelivery()
        val publisher = NotePublisher(
            FakeNoteStore(),
            followers(
                "https://a.example/users/alice" to null,
                "https://b.example/users/bob" to null,
            ),
            delivery,
        )

        val published = publisher.publish(sender, "<p>こんにちは</p>")

        assertEquals(2, published.targets)
        assertEquals(2, published.delivered)
        assertEquals(
            listOf("https://a.example/users/alice/inbox", "https://b.example/users/bob/inbox"),
            delivery.delivered.map { it.inbox },
        )

        val activity = AppJson.decodeFromString(CreateNote.serializer(), delivery.delivered.first().body)
        assertEquals("Create", activity.type)
        assertEquals(sender.actorId, activity.actor)
        assertEquals(listOf(PUBLIC_AUDIENCE), activity.to)
        assertEquals(listOf(sender.followers), activity.cc)

        assertEquals("Note", activity.target.type)
        assertEquals("<p>こんにちは</p>", activity.target.content)
        assertEquals("https://example.com/notes/${published.publicId}", activity.target.id)
        // 外側の Create が @context を持つので、中で重ねない
        assertNull(activity.target.context)
    }

    @Test
    fun `sharedInbox があればまとめて 1 通にする`() = runBlocking {
        val delivery = TestDelivery()
        val publisher = NotePublisher(
            FakeNoteStore(),
            followers(
                "https://a.example/users/alice" to "https://a.example/inbox",
                "https://a.example/users/bob" to "https://a.example/inbox",
                "https://b.example/users/carol" to null,
            ),
            delivery,
        )

        val published = publisher.publish(sender, "<p>まとめ</p>")

        // 同じインスタンスの 2 人は 1 通で済む
        assertEquals(2, published.targets)
        assertEquals(
            listOf("https://a.example/inbox", "https://b.example/users/carol/inbox"),
            delivery.delivered.map { it.inbox },
        )
    }

    @Test
    fun `配る前に記録する`() = runBlocking {
        val notes = FakeNoteStore()
        val publisher = NotePublisher(notes, followers("https://a.example/users/alice" to null), TestDelivery())

        val published = publisher.publish(sender, "<p>本文</p>")

        // 相手は受け取った直後にパーマリンクを引きに来ることがある。
        // 配信が先だとそこで 404 を返してしまう
        val stored = notes.find(published.publicId)
        assertTrue(stored != null, "配信した投稿が記録されていない")
        assertEquals("<p>本文</p>", stored.contentHtml)
        assertEquals(TestLocalActor.USERNAME, stored.username)
    }

    @Test
    fun `配信に失敗しても投稿は残る`() = runBlocking {
        val notes = FakeNoteStore()
        val publisher = NotePublisher(
            notes,
            followers("https://a.example/users/alice" to null),
            TestDelivery(result = DeliveryResult.Failed("届かない")),
        )

        val published = publisher.publish(sender, "<p>本文</p>")

        assertEquals(1, published.targets)
        assertEquals(0, published.delivered)
        assertEquals(1, notes.added.size)
    }

    @Test
    fun `Accept を返せていない相手には送らない`() = runBlocking {
        val delivery = TestDelivery()
        val pending = FakeFollowerStore().apply {
            record(
                username = TestLocalActor.USERNAME,
                follower = RemoteActor(
                    actorId = "https://a.example/users/alice",
                    inbox = "https://a.example/users/alice/inbox",
                    sharedInbox = null,
                    publicKeyPem = "pem",
                ),
                followActivityUri = "https://remote.example/activities/1",
                receivedAt = now,
            )
        }

        val published = NotePublisher(FakeNoteStore(), pending, delivery).publish(sender, "<p>本文</p>")

        // 相手から見てフォローが成立していないので、送ると知らないアクターからの投稿になる
        assertEquals(0, published.targets)
        assertEquals(emptyList(), delivery.delivered)
    }
}
