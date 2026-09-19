package net.matsudamper.mastodon.rss.inbox

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.FakeNoteStore
import net.matsudamper.mastodon.rss.FakeReactionStore
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.TestRemoteActor
import net.matsudamper.mastodon.rss.activity.InboxActivity
import net.matsudamper.mastodon.rss.entity.PublicNoteId
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.note.StoredNote

// お気に入りとスタンプを受け取る。
// Mastodon は content の無い Like を、Misskey は content に絵文字を載せた
// Like か EmojiReact を送ってくる。
class ReactionHandlerTest {
    private val recipient = TestLocalActor.urls

    private val notePublicId = PublicNoteId("note1")

    private val noteUrl = "https://${TestLocalActor.DOMAIN}/notes/${notePublicId.value}"

    private fun notes(username: String = TestLocalActor.USERNAME): FakeNoteStore = FakeNoteStore().apply {
        add(
            StoredNote(
                publicId = notePublicId,
                username = username,
                contentHtml = "<p>本文</p>",
                publishedAt = Instant.parse("2026-08-10T00:00:00Z"),
            ),
        )
    }

    private suspend fun handle(
        json: String,
        type: String = ReactionHandler.LIKE_TYPE,
        notes: FakeNoteStore = notes(),
        reactions: FakeReactionStore = FakeReactionStore(),
    ): FakeReactionStore {
        val rawActivityJson = AppJson.parseToJsonElement(json) as JsonObject
        ReactionHandler(
            type = type,
            domain = TestLocalActor.DOMAIN,
            notes = notes,
            reactions = reactions,
        ).handle(
            recipient = recipient,
            verifiedSignerActorId = TestRemoteActor.ACTOR_ID,
            activity = AppJson.decodeFromJsonElement(InboxActivity.serializer(), rawActivityJson),
            rawActivityJson = rawActivityJson,
        )
        return reactions
    }

    @Test
    fun `content の無い Like はお気に入りとして記録する`() = runBlocking {
        val reactions = handle(
            """
            {"id":"https://remote.example/likes/1","type":"Like",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"$noteUrl"}
            """.trimIndent(),
        )

        val recorded = reactions.rows.single()
        assertEquals(notePublicId, recorded.notePublicId)
        assertEquals(TestRemoteActor.ACTOR_ID, recorded.actorUri)
        assertEquals("", recorded.emoji)
        assertNull(recorded.emojiImageUrl)
    }

    @Test
    fun `content の絵文字をスタンプとして記録する`() = runBlocking {
        val reactions = handle(
            """
            {"id":"https://remote.example/likes/1","type":"Like",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"$noteUrl","content":"👍"}
            """.trimIndent(),
        )

        assertEquals("👍", reactions.rows.single().emoji)
    }

    @Test
    fun `カスタム絵文字の画像を tag から取る`() = runBlocking {
        val reactions = handle(
            """
            {"id":"https://remote.example/reacts/1","type":"EmojiReact",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"$noteUrl","content":":kawaii@misskey.example:",
             "tag":[{"type":"Emoji","name":":kawaii@misskey.example:",
                     "icon":{"type":"Image","url":"https://misskey.example/emoji/kawaii.png"}}]}
            """.trimIndent(),
            type = ReactionHandler.EMOJI_REACT_TYPE,
        )

        val recorded = reactions.rows.single()
        assertEquals(":kawaii@misskey.example:", recorded.emoji)
        assertEquals("https://misskey.example/emoji/kawaii.png", recorded.emojiImageUrl)
    }

    @Test
    fun `別のアカウントの投稿への反応は記録しない`() = runBlocking {
        val reactions = handle(
            """
            {"id":"https://remote.example/likes/1","type":"Like",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"$noteUrl"}
            """.trimIndent(),
            notes = notes(username = TestLocalActor.STORED_USERNAME),
        )

        assertTrue(reactions.rows.isEmpty())
    }

    @Test
    fun `よそのサーバーの投稿への反応は記録しない`() = runBlocking {
        val reactions = handle(
            """
            {"id":"https://remote.example/likes/1","type":"Like",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"https://other.example/notes/1"}
            """.trimIndent(),
        )

        assertTrue(reactions.rows.isEmpty())
    }

    @Test
    fun `id の無い反応は記録しない`() = runBlocking {
        val reactions = handle(
            """
            {"type":"Like","actor":"${TestRemoteActor.ACTOR_ID}","object":"$noteUrl"}
            """.trimIndent(),
        )

        assertTrue(reactions.rows.isEmpty())
    }
}
