package net.matsudamper.mastodon.rss.inbox

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.TestActorKey
import net.matsudamper.mastodon.rss.TestPublicKeys
import net.matsudamper.mastodon.rss.TestRemoteActor
import net.matsudamper.mastodon.rss.TestServerEnv
import net.matsudamper.mastodon.rss.httpsignature.PublicKeys
import net.matsudamper.mastodon.rss.httpsignature.TestSigning
import net.matsudamper.mastodon.rss.module
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

// 相手のサーバーからアクティビティが POST されてくる口。
// 署名が通ったかどうかが、送り主を確かめる唯一の根拠になる。
class InboxRoutesTest {
    /**
     * テストクライアントが送る Host。署名対象に入るので、送信側と揃える必要がある。
     */
    private val host = "localhost"

    private fun follow(actor: String = TestRemoteActor.ACTOR_ID): ByteArray =
        """
        {"id":"https://remote.example/activities/1","type":"Follow",
         "actor":"$actor","object":"https://example.com/users/admin"}
        """.trimIndent().toByteArray()

    private fun ApplicationTestBuilder.installModule(publicKeys: PublicKeys = TestRemoteActor.publicKeys()) {
        application {
            module(FakeRepositories(), TestActorKey.value, TestServerEnv.value, publicKeys)
        }
    }

    private suspend fun ApplicationTestBuilder.postInbox(
        path: String = "/users/admin/inbox",
        body: ByteArray,
        headers: Map<String, String> =
            TestSigning.headers(
                requestTarget = path,
                host = host,
                date = Instant.now(),
                body = body,
            ),
    ): HttpResponse =
        client.post(path) {
            headers.forEach { (name, value) -> header(name, value) }
            setBody(body)
        }

    @Test
    fun `署名が正しければ202で受ける`() =
        testApplication {
            installModule()

            val response = postInbox(body = follow())

            assertEquals(HttpStatusCode.Accepted, response.status)
        }

    @Test
    fun `使い捨てアクター宛でも受ける`() =
        testApplication {
            installModule()

            val path = "/users/test-1/inbox"
            val response = postInbox(path = path, body = follow())

            assertEquals(HttpStatusCode.Accepted, response.status)
        }

    @Test
    fun `Signature ヘッダが無ければ401`() =
        testApplication {
            installModule()

            val body = follow()
            val response =
                postInbox(
                    body = body,
                    headers =
                        TestSigning
                            .headers(
                                requestTarget = "/users/admin/inbox",
                                host = host,
                                date = Instant.now(),
                                body = body,
                            ).filterKeys { it != "Signature" },
                )

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `知らないアクター宛は404`() =
        testApplication {
            installModule()

            val response = postInbox(path = "/users/other/inbox", body = follow())

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `公開鍵を引けなければ401`() =
        testApplication {
            installModule(TestPublicKeys())

            val response = postInbox(body = follow())

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `ボディを差し替えたら401`() =
        testApplication {
            installModule()

            val signed = follow()
            val response =
                postInbox(
                    body = """{"type":"Undo"}""".toByteArray(),
                    headers =
                        TestSigning.headers(
                            requestTarget = "/users/admin/inbox",
                            host = host,
                            date = Instant.now(),
                            body = signed,
                        ),
                )

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `actor が署名者と違えば401`() =
        testApplication {
            installModule()

            // 署名は自分の鍵で正しく作りつつ、actor だけ他人を名乗る
            val response = postInbox(body = follow(actor = "https://remote.example/users/bob"))

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `JSON として読めないボディは400`() =
        testApplication {
            installModule()

            val response = postInbox(body = "not json".toByteArray())

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `GET では受けない`() =
        testApplication {
            installModule()

            // inbox は POST だけ。GET は静的配信のフォールバックに落ちて 404 になる
            assertEquals(HttpStatusCode.NotFound, client.get("/users/admin/inbox").status)
        }
}
