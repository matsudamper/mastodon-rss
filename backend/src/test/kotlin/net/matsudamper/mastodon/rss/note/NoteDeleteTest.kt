package net.matsudamper.mastodon.rss.note

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import net.matsudamper.mastodon.rss.FakeFollowerStore
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.TestDelivery
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.TestWebPageUrls
import net.matsudamper.mastodon.rss.actor.RemoteActor
import net.matsudamper.mastodon.rss.entity.PublicNoteId
import net.matsudamper.mastodon.rss.logic.NoteEnqueuer
import net.matsudamper.mastodon.rss.logic.RepositoryNoteStore
import net.matsudamper.mastodon.rss.repository.Note

// 削除はキューに載せずその場で配る。管理画面はこの経路を直に呼ぶ
class NoteDeleteTest {
    private val repositories = FakeRepositories()

    private val notes = RepositoryNoteStore(repositories.notes)

    private val delivery = TestDelivery()

    private val followers = FakeFollowerStore()

    private val publisher = NotePublisher(notes, followers, delivery, TestWebPageUrls)

    private val enqueuer = NoteEnqueuer(
        publisher = publisher,
        followers = repositories.followers,
        deliveryQueue = repositories.deliveryQueue,
    )

    private fun added(): List<Note> = repositories.notes.all()

    @Test
    fun `投稿を消すと記録が消え Delete を配る`() = runBlocking {
        val follower = RemoteActor(
            actorId = "https://remote.example/users/follower",
            inbox = "https://remote.example/users/follower/inbox",
            sharedInbox = null,
            publicKeyPem = "pem",
        )
        followers.record(
            username = TestLocalActor.USERNAME,
            follower = follower,
            followActivityUri = "https://remote.example/follows/1",
            receivedAt = FOLLOWED_AT,
        )
        followers.markAccepted(
            username = TestLocalActor.USERNAME,
            followerActorUri = follower.actorId,
            acceptedAt = FOLLOWED_AT,
        )
        val queued = assertNotNull(
            enqueuer.enqueue(sender = TestLocalActor.urls, contentHtml = "<p>本文</p>", feedItemId = null),
        )

        val deleted = assertNotNull(publisher.delete(sender = TestLocalActor.urls, publicId = queued.publicId))

        assertEquals(queued.publicId, deleted.publicId)
        assertEquals(emptyList(), added())

        // 消したことは配って初めて伝わる。届かないとフォロワーのタイムラインに残る
        val body = delivery.delivered.last().body
        assertContains(body, """"type":"Delete"""")
        assertContains(body, """"type":"Tombstone"""")
        assertContains(body, queued.url)
        // 宛先が元の投稿と揃っていないと、受け取っても消さない実装がある
        assertContains(body, TestLocalActor.urls.followers)
    }

    @Test
    fun `無い投稿と他のアカウントの投稿は消せない`() = runBlocking {
        val queued = assertNotNull(
            enqueuer.enqueue(sender = TestLocalActor.urls, contentHtml = "<p>本文</p>", feedItemId = null),
        )
        val other = assertNotNull(TestLocalActor.directory.resolve(TestLocalActor.STORED_USERNAME))

        assertNull(publisher.delete(sender = TestLocalActor.urls, publicId = PublicNoteId("missing")))
        // 他のアカウントの投稿を id だけで消せないようにする
        assertNull(publisher.delete(sender = other, publicId = queued.publicId))

        assertEquals(1, added().size)
    }

    private companion object {
        val FOLLOWED_AT: Instant = Instant.parse("2026-08-16T00:00:00Z")
    }
}
