package net.matsudamper.mastodon.rss.inbox

import io.ktor.http.Headers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.matsudamper.mastodon.rss.TestRemoteActor
import net.matsudamper.mastodon.rss.TestRemoteActors
import net.matsudamper.mastodon.rss.activitypub.InboxActivity
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.actor.RemoteActors
import net.matsudamper.mastodon.rss.httpsignature.HttpSignatureVerifier
import net.matsudamper.mastodon.rss.httpsignature.SignedRequest
import net.matsudamper.mastodon.rss.httpsignature.TestSigning
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

// 受け取ったものを信用してよいかの判断と、種類ごとの振り分け。
// HTTP の status への変換は inboxRoutes 側なので、ここはサーバーを立てずに確かめる。
class InboxServiceTest {
    private val recipient = ActorUrls(domain = "example.com", username = "admin")

    private val host = "example.com"

    private val path = "/users/admin/inbox"

    // 呼ばれたことと、渡ってきたものを記録するだけのハンドラ
    private class RecordingHandler(
        override val type: String,
    ) : InboxActivityHandler {
        val calls: MutableList<Call> = mutableListOf()

        override suspend fun handle(
            recipient: ActorUrls,
            signer: String,
            activity: InboxActivity,
            raw: JsonObject,
        ) {
            calls += Call(recipient = recipient, signer = signer, activity = activity, raw = raw)
        }

        class Call(
            val recipient: ActorUrls,
            val signer: String,
            val activity: InboxActivity,
            val raw: JsonObject,
        )
    }

    private fun follow(
        actor: String = TestRemoteActor.ACTOR_ID,
        target: String = recipient.actorId,
    ): ByteArray =
        """
        {"id":"https://remote.example/activities/1","type":"Follow",
         "actor":"$actor","object":"$target"}
        """.trimIndent().toByteArray()

    private fun undo(actor: String = TestRemoteActor.ACTOR_ID): ByteArray =
        """
        {"id":"https://remote.example/activities/2","type":"Undo",
         "actor":"$actor","object":"https://remote.example/activities/1"}
        """.trimIndent().toByteArray()

    /**
     * 相手のサーバーが送ってくる形の、署名済みリクエストを組む。
     *
     * @param signedBody 署名を作る対象。[body] と別のものを渡すと、
     *   送信後にボディを差し替えられた形になる
     */
    private fun signedRequest(
        body: ByteArray,
        signedBody: ByteArray = body,
    ): SignedRequest {
        val headers =
            TestSigning.headers(
                requestTarget = path,
                host = host,
                date = Instant.now(),
                body = signedBody,
            )

        return SignedRequest(
            method = "POST",
            requestTarget = path,
            headers = Headers.build { headers.forEach { (name, value) -> append(name, value) } },
            body = body,
        )
    }

    private fun service(
        handlers: List<InboxActivityHandler>,
        remoteActors: RemoteActors = TestRemoteActor.remoteActors(),
    ): InboxService = InboxService(verifier = HttpSignatureVerifier(remoteActors), handlers = handlers)

    @Test
    fun `type が一致するハンドラに渡す`() =
        runBlocking {
            val handler = RecordingHandler("Follow")

            val result = service(listOf(handler)).receive(recipient, signedRequest(follow()))

            assertEquals(InboxResult.Accepted, result)

            val call = handler.calls.single()
            assertEquals(recipient, call.recipient)
            // 渡すのは署名を検証した結果の持ち主で、ボディの自称ではない
            assertEquals(TestRemoteActor.ACTOR_ID, call.signer)
            assertEquals("Follow", call.activity.type)
            // Accept に丸ごと入れるので、元の JSON も渡っている必要がある
            assertEquals("https://remote.example/activities/1", call.raw["id"]?.jsonPrimitive?.content)
        }

    @Test
    fun `引き当てられない type は何もせずに受け取る`() =
        runBlocking {
            val handler = RecordingHandler("Follow")

            val result = service(listOf(handler)).receive(recipient, signedRequest(undo()))

            // 未対応のアクティビティに 5xx を返すと相手は同じものを送り直し続ける
            assertEquals(InboxResult.Accepted, result)
            assertTrue(handler.calls.isEmpty(), "${handler.calls.size}")
        }

    @Test
    fun `公開鍵を引けなければ拒否してハンドラを呼ばない`() =
        runBlocking {
            val handler = RecordingHandler("Follow")

            val result =
                service(listOf(handler), remoteActors = TestRemoteActors())
                    .receive(recipient, signedRequest(follow()))

            assertEquals(InboxResult.Unauthorized, result)
            assertTrue(handler.calls.isEmpty(), "${handler.calls.size}")
        }

    @Test
    fun `ボディを差し替えたら拒否する`() =
        runBlocking {
            val handler = RecordingHandler("Follow")

            // 署名は元のボディに対して作り、送る中身だけ入れ替える
            val request = signedRequest(body = undo(), signedBody = follow())
            val result = service(listOf(handler)).receive(recipient, request)

            assertEquals(InboxResult.Unauthorized, result)
            assertTrue(handler.calls.isEmpty(), "${handler.calls.size}")
        }

    @Test
    fun `JSON として読めないボディは弾く`() =
        runBlocking {
            val handler = RecordingHandler("Follow")

            val result = service(listOf(handler)).receive(recipient, signedRequest("not json".toByteArray()))

            assertEquals(InboxResult.BadRequest, result)
            assertTrue(handler.calls.isEmpty(), "${handler.calls.size}")
        }

    @Test
    fun `actor が署名者と違えば拒否する`() =
        runBlocking {
            val handler = RecordingHandler("Follow")

            // 署名は自分の鍵で正しく作りつつ、actor だけ他人を名乗る
            val request = signedRequest(follow(actor = "https://remote.example/users/bob"))
            val result = service(listOf(handler)).receive(recipient, request)

            assertEquals(InboxResult.Unauthorized, result)
            assertTrue(handler.calls.isEmpty(), "${handler.calls.size}")
        }

    @Test
    fun `actor が無いアクティビティも受け取る`() =
        runBlocking {
            val handler = RecordingHandler("Follow")
            val body =
                """
                {"id":"https://remote.example/activities/3","type":"Follow","object":"${recipient.actorId}"}
                """.trimIndent().toByteArray()

            val result = service(listOf(handler)).receive(recipient, signedRequest(body))

            // 名乗っていない以上なりすましようが無いので、署名した相手のものとして扱う
            assertEquals(InboxResult.Accepted, result)
            val call = handler.calls.singleOrNull() ?: fail("ハンドラが呼ばれていない")
            assertNull(call.activity.actorId)
            assertEquals(TestRemoteActor.ACTOR_ID, call.signer)
        }

    @Test
    fun `同じ type のハンドラが複数あれば作れない`() {
        // 黙って片方が捨てられると、処理されないアクティビティが相手からは無反応に見える
        val failure =
            assertFailsWith<IllegalArgumentException> {
                service(listOf(RecordingHandler("Follow"), RecordingHandler("Follow")))
            }

        assertTrue(failure.message?.contains("Follow") == true, "${failure.message}")
    }
}
