package net.matsudamper.mastodon.rss.note

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.TestWebPageUrls
import net.matsudamper.mastodon.rss.entity.PublicNoteId
import net.matsudamper.mastodon.rss.logic.NoteEnqueuer
import net.matsudamper.mastodon.rss.logic.RepositoryNoteStore
import net.matsudamper.mastodon.rss.repository.DeliveryKind
import net.matsudamper.mastodon.rss.repository.IncomingFollow
import net.matsudamper.mastodon.rss.repository.NewRemoteActor
import net.matsudamper.mastodon.rss.repository.Note
import net.matsudamper.mastodon.rss.repository.RemoteActorProfile

// 投稿を消すと記録が消えて、消したことの配信が投函される。管理画面はこの経路を通る
class NoteDeleteTest {
    private val repositories = FakeRepositories()

    private val notes = RepositoryNoteStore(repositories.notes)

    private val publisher = NotePublisher(notes, TestWebPageUrls)

    private val enqueuer = NoteEnqueuer(
        publisher = publisher,
        followers = repositories.followers,
        deliveryQueue = repositories.deliveryQueue,
    )

    private fun added(): List<Note> = repositories.notes.all()

    @Test
    fun `投稿を消すと記録が消え Delete を配る`() = runBlocking {
        addAcceptedFollower()
        val queued = enqueuer.enqueue(sender = TestLocalActor.urls, contentHtml = "<p>本文</p>")

        val deleted = assertNotNull(enqueuer.enqueueDeletion(sender = TestLocalActor.urls, publicId = queued.publicId))

        assertEquals(queued.publicId, deleted.publicId)
        assertEquals(emptyList(), added())

        // 消したことは配って初めて伝わる。届かないとフォロワーのタイムラインに残る
        val row = repositories.deliveryQueue.rows().single()
        assertEquals(DeliveryKind.DELETE_NOTE, row.kind)
        val body = assertNotNull(row.body)
        assertContains(body, """"type":"Delete"""")
        assertContains(body, """"type":"Tombstone"""")
        assertContains(body, queued.url)
        // 宛先が元の投稿と揃っていないと、受け取っても消さない実装がある
        assertContains(body, TestLocalActor.urls.followers)
    }

    @Test
    fun `無い投稿と他のアカウントの投稿は消せない`() = runBlocking {
        val queued = enqueuer.enqueue(sender = TestLocalActor.urls, contentHtml = "<p>本文</p>")
        val other = assertNotNull(TestLocalActor.directory.resolve(TestLocalActor.STORED_USERNAME))

        assertNull(enqueuer.enqueueDeletion(sender = TestLocalActor.urls, publicId = PublicNoteId("missing")))
        // 他のアカウントの投稿を id だけで消せないようにする
        assertNull(enqueuer.enqueueDeletion(sender = other, publicId = queued.publicId))

        assertEquals(1, added().size)
    }

    /**
     * 配信先になるフォロワーを 1 人だけ用意する
     */
    private fun addAcceptedFollower() {
        val followerActorUri = "https://remote.example/users/follower"
        repositories.followers.record(
            IncomingFollow(
                username = TestLocalActor.USERNAME,
                follower = NewRemoteActor(
                    actorUri = followerActorUri,
                    inbox = "$followerActorUri/inbox",
                    sharedInbox = null,
                    publicKeyPem = "pem",
                    profile = RemoteActorProfile(preferredUsername = null, displayName = null, profileUrl = null, iconUrl = null),
                ),
                followActivityUri = "https://remote.example/follows/1",
                receivedAt = FOLLOWED_AT,
                acceptBody = """{"type":"Accept"}""",
            ),
        )
        // Accept が届いて初めてフォロワーになる。投函した行はここで消える
        repositories.deliveryQueue.claim(now = FOLLOWED_AT, limit = 10).forEach {
            repositories.deliveryQueue.markDelivered(id = it.id, deliveredAt = FOLLOWED_AT)
        }
    }

    private companion object {
        val FOLLOWED_AT: Instant = Instant.parse("2026-08-16T00:00:00Z")
    }
}
