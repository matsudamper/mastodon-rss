package net.matsudamper.mastodon.rss.inbox

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.FakeFollowerStore
import net.matsudamper.mastodon.rss.FakeReactionStore
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.TestRemoteActor
import net.matsudamper.mastodon.rss.activity.InboxActivity
import net.matsudamper.mastodon.rss.actor.RemoteActor
import net.matsudamper.mastodon.rss.entity.PublicNoteId
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.reaction.ReceivedReaction

// Undo は取り消せるものが 1 つではないので、フォロー解除と反応の取り消しに振り分ける。
class UndoHandlerTest {
    private val now = Instant.parse("2026-08-10T00:00:00Z")

    private val notePublicId = PublicNoteId("note1")

    private val noteUrl = "https://${TestLocalActor.DOMAIN}/notes/${notePublicId.value}"

    private val followUri = "https://remote.example/activities/1"

    private val likeUri = "https://remote.example/likes/1"

    private fun followers(): FakeFollowerStore = FakeFollowerStore().apply {
        record(
            username = TestLocalActor.USERNAME,
            follower = RemoteActor(
                actorId = TestRemoteActor.ACTOR_ID,
                inbox = TestRemoteActor.INBOX,
                sharedInbox = null,
                publicKeyPem = "pem",
            ),
            followActivityUri = followUri,
            receivedAt = now,
            acceptBody = "{}",
        )
        markAccepted(TestLocalActor.USERNAME, TestRemoteActor.ACTOR_ID)
    }

    private fun reactions(emoji: String): FakeReactionStore = FakeReactionStore().apply {
        add(
            ReceivedReaction(
                notePublicId = notePublicId,
                actorUri = TestRemoteActor.ACTOR_ID,
                activityUri = likeUri,
                emoji = emoji,
                emojiImageUrl = null,
                receivedAt = now,
            ),
        )
    }

    private suspend fun handle(
        json: String,
        followers: FakeFollowerStore,
        reactions: FakeReactionStore,
    ) {
        val rawActivityJson = AppJson.parseToJsonElement(json) as JsonObject
        UndoHandler(
            reactions = UndoReactionHandler(domain = TestLocalActor.DOMAIN, reactions = reactions),
            follows = UndoFollowHandler(followers),
        ).handle(
            recipient = TestLocalActor.urls,
            verifiedSignerActorId = TestRemoteActor.ACTOR_ID,
            activity = AppJson.decodeFromJsonElement(InboxActivity.serializer(), rawActivityJson),
            rawActivityJson = rawActivityJson,
        )
    }

    @Test
    fun `object に Like が埋まっていればスタンプを取り消す`() = runBlocking {
        val followers = followers()
        val reactions = reactions(emoji = "👍")

        handle(
            """
            {"id":"https://remote.example/undo/1","type":"Undo","actor":"${TestRemoteActor.ACTOR_ID}",
             "object":{"id":"$likeUri","type":"Like","actor":"${TestRemoteActor.ACTOR_ID}",
                       "object":"$noteUrl","content":"👍"}}
            """.trimIndent(),
            followers = followers,
            reactions = reactions,
        )

        assertTrue(reactions.rows.isEmpty())
        // フォローは巻き込まれない
        assertEquals(1, followers.rows.size)
    }

    @Test
    fun `元のアクティビティの id が無ければ投稿と絵文字で取り消す`() = runBlocking {
        val reactions = reactions(emoji = ":kawaii:")

        handle(
            """
            {"id":"https://remote.example/undo/1","type":"Undo","actor":"${TestRemoteActor.ACTOR_ID}",
             "object":{"type":"EmojiReact","actor":"${TestRemoteActor.ACTOR_ID}",
                       "object":"$noteUrl","content":":kawaii:"}}
            """.trimIndent(),
            followers = followers(),
            reactions = reactions,
        )

        assertTrue(reactions.rows.isEmpty())
    }

    @Test
    fun `id だけの Undo は記録している反応に当たれば取り消す`() = runBlocking {
        val followers = followers()
        val reactions = reactions(emoji = "👍")

        handle(
            """
            {"id":"https://remote.example/undo/1","type":"Undo",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"$likeUri"}
            """.trimIndent(),
            followers = followers,
            reactions = reactions,
        )

        assertTrue(reactions.rows.isEmpty())
        assertEquals(1, followers.rows.size)
    }

    @Test
    fun `id だけの Undo が反応に当たらなければフォローを解除する`() = runBlocking {
        val followers = followers()
        val reactions = reactions(emoji = "👍")

        handle(
            """
            {"id":"https://remote.example/undo/1","type":"Undo",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"$followUri"}
            """.trimIndent(),
            followers = followers,
            reactions = reactions,
        )

        assertTrue(followers.rows.isEmpty())
        assertEquals(1, reactions.rows.size)
    }

    @Test
    fun `object に Follow が埋まっていればフォローを解除する`() = runBlocking {
        val followers = followers()
        val reactions = reactions(emoji = "👍")

        handle(
            """
            {"id":"https://remote.example/undo/1","type":"Undo","actor":"${TestRemoteActor.ACTOR_ID}",
             "object":{"id":"$followUri","type":"Follow","actor":"${TestRemoteActor.ACTOR_ID}",
                       "object":"${TestLocalActor.urls.actorId}"}}
            """.trimIndent(),
            followers = followers,
            reactions = reactions,
        )

        assertTrue(followers.rows.isEmpty())
        assertEquals(1, reactions.rows.size)
    }
}
