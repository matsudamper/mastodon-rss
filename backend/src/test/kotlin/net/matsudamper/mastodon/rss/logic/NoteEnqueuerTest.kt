package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import net.matsudamper.mastodon.rss.FakeFollowerStore
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.TestDelivery
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.TestWebPageUrls
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.note.NotePublisher
import net.matsudamper.mastodon.rss.repository.IncomingFollow
import net.matsudamper.mastodon.rss.repository.NewRemoteActor
import net.matsudamper.mastodon.rss.repository.Note

// 記録と宛先ごとのキュー行が 1 回で確定するところ。送るのはここではない
class NoteEnqueuerTest {
    private val repositories = FakeRepositories()

    private val notes = RepositoryNoteStore(repositories.notes)

    private val delivery = TestDelivery()

    private fun enqueuer(): NoteEnqueuer = NoteEnqueuer(
        publisher = NotePublisher(notes, FakeFollowerStore(), delivery, TestWebPageUrls),
        followers = repositories.followers,
        deliveryQueue = repositories.deliveryQueue,
    )

    private fun added(): List<Note> = repositories.notes.all()

    @Test
    fun `保存されているアカウントからも投函できる`() = runBlocking {
        val sender = assertNotNull(TestLocalActor.directory.resolve(TestLocalActor.STORED_USERNAME))

        enqueuer().enqueue(sender = sender, contentHtml = "<p>本文</p>")

        assertEquals(TestLocalActor.STORED_USERNAME, added().single().username)
    }

    @Test
    fun `投稿はフォロワーの inbox ごとにキューへ入り その場では送らない`() = runBlocking {
        val follower = NewRemoteActor(
            actorUri = "https://remote.example/users/follower",
            inbox = "https://remote.example/users/follower/inbox",
            sharedInbox = "https://remote.example/inbox",
            publicKeyPem = "pem",
        )
        repositories.followers.record(
            IncomingFollow(
                username = TestLocalActor.USERNAME,
                follower = follower,
                followActivityUri = "https://remote.example/follows/1",
                receivedAt = FOLLOWED_AT,
                acceptBody = """{"type":"Accept"}""",
            ),
        )
        repositories.followers.markAccepted(
            username = TestLocalActor.USERNAME,
            followerActorUri = follower.actorUri,
        )

        val queued = enqueuer().enqueue(sender = SENDER, contentHtml = "<p>本文</p>")

        assertEquals(1, added().size)
        // 送るのは配信ワーカー。ここで送ってしまうと、落ちたときに送り直せない
        assertEquals(emptyList(), delivery.delivered)
        val row = repositories.deliveryQueue.rows().single()
        assertEquals("https://remote.example/inbox", row.inbox)
        assertEquals(TestLocalActor.USERNAME, row.username)
        assertContains(assertNotNull(row.body), queued.url)
    }

    private companion object {
        val SENDER: ActorUrls = TestLocalActor.urls
        val FOLLOWED_AT: Instant = Instant.parse("2026-08-16T00:00:00Z")
    }
}
