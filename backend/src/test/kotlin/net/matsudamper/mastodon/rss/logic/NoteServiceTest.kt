package net.matsudamper.mastodon.rss.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import net.matsudamper.mastodon.rss.FakeFollowerStore
import net.matsudamper.mastodon.rss.FakeNoteStore
import net.matsudamper.mastodon.rss.TestDelivery
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.note.NotePublisher

// 管理画面から投稿する経路。
// 本文をプレーンテキストで受けて HTML に組み立てるところがここの責務になる。
class NoteServiceTest {
    private val notes = FakeNoteStore()

    private val delivery = TestDelivery()

    private fun service(): NoteService = NoteService(
        directory = TestLocalActor.directory,
        publisher = NotePublisher(notes, FakeFollowerStore(), delivery),
        notes = notes,
    )

    @Test
    fun `段落と改行だけの HTML にする`() = runBlocking {
        val result = service().post(username = TestLocalActor.USERNAME, body = "こんにちは\n世界\n\n2 つめの段落")

        assertIs<NoteService.PostResult.Success>(result)
        assertEquals("<p>こんにちは<br>世界</p><p>2 つめの段落</p>", notes.added.single().contentHtml)
    }

    @Test
    fun `HTML はそのまま流さない`() = runBlocking {
        service().post(username = TestLocalActor.USERNAME, body = "<script>alert(1)</script>")

        // 管理画面を通して任意のタグをフォロワーに配れないようにする
        assertEquals("<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>", notes.added.single().contentHtml)
    }

    @Test
    fun `保存されているアカウントからも投稿できる`() = runBlocking {
        val result = service().post(username = TestLocalActor.STORED_USERNAME, body = "本文")

        assertIs<NoteService.PostResult.Success>(result)
        assertEquals(TestLocalActor.STORED_USERNAME, notes.added.single().username)
    }

    @Test
    fun `知らないアカウントでは投稿しない`() = runBlocking {
        val result = service().post(username = "nobody", body = "本文")

        assertIs<NoteService.PostResult.Failure>(result)
        assertEquals(true, result.unknownAccount)
        assertEquals(emptyList(), notes.added)
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

        assertEquals(emptyList(), notes.added)
    }
}
