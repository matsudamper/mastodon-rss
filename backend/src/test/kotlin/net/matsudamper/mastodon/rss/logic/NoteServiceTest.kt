package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import net.matsudamper.mastodon.rss.FakeFollowerStore
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.TestDelivery
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.TestWebPageUrls
import net.matsudamper.mastodon.rss.actor.RemoteActor
import net.matsudamper.mastodon.rss.note.NotePublisher
import net.matsudamper.mastodon.rss.repository.IncomingFollow
import net.matsudamper.mastodon.rss.repository.NewRemoteActor
import net.matsudamper.mastodon.rss.repository.Note
import net.matsudamper.mastodon.rss.shared.PublicNoteId

// 管理画面から投稿する経路。
// 本文をプレーンテキストで受けて HTML に組み立てるところと、
// 記録と配信の投函が 1 回で確定するところがここの責務になる。
class NoteServiceTest {
    private val repositories = FakeRepositories()

    private val notes = RepositoryNoteStore(repositories.notes)

    private val delivery = TestDelivery()

    private fun service(followers: FakeFollowerStore = FakeFollowerStore()): NoteService {
        val publisher = NotePublisher(notes, followers, delivery, TestWebPageUrls)
        return NoteService(
            directory = TestLocalActor.directory,
            publisher = publisher,
            poster = NotePoster(
                publisher = publisher,
                followers = repositories.followers,
                deliveryQueue = repositories.deliveryQueue,
            ),
            notes = notes,
        )
    }

    private fun added(): List<Note> = repositories.notes.all()

    @Test
    fun `段落と改行だけの HTML にする`() = runBlocking {
        val result = service().post(username = TestLocalActor.USERNAME, body = "こんにちは\n世界\n\n2 つめの段落")

        assertIs<NoteService.PostResult.Success>(result)
        assertEquals("<p>こんにちは<br>世界</p><p>2 つめの段落</p>", added().single().contentHtml)
    }

    @Test
    fun `HTML はそのまま流さない`() = runBlocking {
        service().post(username = TestLocalActor.USERNAME, body = "<script>alert(1)</script>")

        // 管理画面を通して任意のタグをフォロワーに配れないようにする
        assertEquals("<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>", added().single().contentHtml)
    }

    @Test
    fun `保存されているアカウントからも投稿できる`() = runBlocking {
        val result = service().post(username = TestLocalActor.STORED_USERNAME, body = "本文")

        assertIs<NoteService.PostResult.Success>(result)
        assertEquals(TestLocalActor.STORED_USERNAME, added().single().username)
    }

    @Test
    fun `知らないアカウントでは投稿しない`() = runBlocking {
        val result = service().post(username = "nobody", body = "本文")

        assertIs<NoteService.PostResult.Failure>(result)
        assertEquals(true, result.unknownAccount)
        assertEquals(emptyList(), added())
    }

    @Test
    fun `続きがあるかは取れた件数で決まる`() = runBlocking {
        repeat(3) { index ->
            service().post(username = TestLocalActor.USERNAME, body = "本文 $index")
        }

        val first = service().notes(username = TestLocalActor.USERNAME, after = null, limit = 2)
        assertEquals(2, first.notes.size)
        assertEquals(true, first.hasMore)

        val next = service().notes(
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
        repeat(2) { index ->
            service().post(username = TestLocalActor.USERNAME, body = "本文 $index")
        }

        val page = service().notes(
            username = TestLocalActor.USERNAME,
            after = null,
            limit = NoteService.MAX_LIST_LIMIT + 1000,
        )

        assertEquals(2, page.notes.size)
    }

    @Test
    fun `知らないアカウントの一覧は空`() = runBlocking {
        val page = service().notes(username = "nobody", after = null, limit = 10)

        assertEquals(emptyList(), page.notes)
        assertEquals(false, page.hasMore)
    }

    @Test
    fun `投稿を消すと記録が消え Delete を配る`() = runBlocking {
        val follower = RemoteActor(
            actorId = "https://remote.example/users/follower",
            inbox = "https://remote.example/users/follower/inbox",
            sharedInbox = null,
            publicKeyPem = "pem",
        )
        val followers = FakeFollowerStore()
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
        val service = service(followers = followers)
        val posted = assertIs<NoteService.PostResult.Success>(
            service.post(username = TestLocalActor.USERNAME, body = "本文"),
        )

        val result = service.delete(
            username = TestLocalActor.USERNAME,
            publicId = PublicNoteId(posted.queued.publicId.value),
        )

        val success = assertIs<NoteService.DeleteResult.Success>(result)
        assertEquals(posted.queued.publicId, success.deleted.publicId)
        assertEquals(emptyList(), added())

        // 消したことは配って初めて伝わる。届かないとフォロワーのタイムラインに残る
        val body = delivery.delivered.last().body
        assertContains(body, """"type":"Delete"""")
        assertContains(body, """"type":"Tombstone"""")
        assertContains(body, posted.queued.url)
        // 宛先が元の投稿と揃っていないと、受け取っても消さない実装がある
        assertContains(body, TestLocalActor.urls.followers)
    }

    @Test
    fun `知らないアカウントと無い投稿は消せない`() = runBlocking {
        val posted = assertIs<NoteService.PostResult.Success>(
            service().post(username = TestLocalActor.USERNAME, body = "本文"),
        )

        assertEquals(
            NoteService.DeleteFailure.UNKNOWN_ACCOUNT,
            assertIs<NoteService.DeleteResult.Failure>(
                service().delete(username = "nobody", publicId = PublicNoteId(posted.queued.publicId.value)),
            ).reason,
        )
        assertEquals(
            NoteService.DeleteFailure.NOT_FOUND,
            assertIs<NoteService.DeleteResult.Failure>(
                service().delete(username = TestLocalActor.USERNAME, publicId = PublicNoteId("missing")),
            ).reason,
        )
        // 他のアカウントの投稿を id だけで消せないようにする
        assertEquals(
            NoteService.DeleteFailure.NOT_FOUND,
            assertIs<NoteService.DeleteResult.Failure>(
                service().delete(
                    username = TestLocalActor.STORED_USERNAME,
                    publicId = PublicNoteId(posted.queued.publicId.value),
                ),
            ).reason,
        )
        assertEquals(1, added().size)
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
            ),
        )
        repositories.followers.markAccepted(
            username = TestLocalActor.USERNAME,
            followerActorUri = follower.actorUri,
            acceptedAt = FOLLOWED_AT,
        )

        val result = service().post(username = TestLocalActor.USERNAME, body = "本文")

        val success = assertIs<NoteService.PostResult.Success>(result)
        assertEquals(1, success.queued.queuedDeliveries)
        assertEquals(1, added().size)
        // 送るのは配信ワーカー。ここで送ってしまうと、落ちたときに送り直せない
        assertEquals(emptyList(), delivery.delivered)
        val row = repositories.deliveryQueue.rows().single()
        assertEquals("https://remote.example/inbox", row.inbox)
        assertEquals(TestLocalActor.USERNAME, row.username)
        assertContains(assertNotNull(row.body), success.queued.url)
    }

    @Test
    fun `空の本文と長すぎる本文は弾く`() = runBlocking {
        val empty = service().post(username = TestLocalActor.USERNAME, body = "   ")
        assertIs<NoteService.PostResult.Failure>(empty)
        assertEquals(true, empty.isEmpty)

        val tooLong = service().post(
            username = TestLocalActor.USERNAME,
            body = "あ".repeat(NoteService.MAX_LENGTH + 1),
        )
        assertIs<NoteService.PostResult.Failure>(tooLong)
        assertEquals(true, tooLong.tooLong)

        assertEquals(emptyList(), added())
    }

    private companion object {
        val FOLLOWED_AT: Instant = Instant.parse("2026-08-16T00:00:00Z")
    }
}
