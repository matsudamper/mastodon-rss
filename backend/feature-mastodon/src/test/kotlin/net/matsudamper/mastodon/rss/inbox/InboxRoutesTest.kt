package net.matsudamper.mastodon.rss.inbox

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.matsudamper.mastodon.rss.TestDelivery
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.TestRemoteActor
import net.matsudamper.mastodon.rss.TestRemoteActors
import net.matsudamper.mastodon.rss.actor.RemoteActors
import net.matsudamper.mastodon.rss.delivery.ActivityDelivery
import net.matsudamper.mastodon.rss.httpsignature.TestSigning
import net.matsudamper.mastodon.rss.json.AppJson

// 相手のサーバーからアクティビティが POST されてくる口。
// 署名が通ったかどうかが、送り主を確かめる唯一の根拠になる。
class InboxRoutesTest {
    /**
     * テストクライアントが送る Host。署名対象に入るので、送信側と揃える必要がある。
     */
    private val host = "localhost"

    private val fixedActor = "https://${TestLocalActor.DOMAIN}/users/${TestLocalActor.USERNAME}"

    private fun follow(
        actor: String = TestRemoteActor.ACTOR_ID,
        target: String = fixedActor,
    ): ByteArray =
        """
        {"id":"https://remote.example/activities/1","type":"Follow",
         "actor":"$actor","object":"$target"}
        """.trimIndent().toByteArray()

    private fun ApplicationTestBuilder.installModule(
        remoteActors: RemoteActors = TestRemoteActor.remoteActors(),
        delivery: ActivityDelivery = TestDelivery(),
    ) {
        application {
            routing {
                inboxRoutes(
                    directory = TestLocalActor.directory,
                    service = InboxService.default(remoteActors = remoteActors, delivery = delivery),
                )
            }
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
    fun `保存されているアカウント宛でも受ける`() =
        testApplication {
            installModule()

            val username = TestLocalActor.STORED_USERNAME
            val path = "/users/$username/inbox"
            val target = "https://${TestLocalActor.DOMAIN}/users/$username"
            val response = postInbox(path = path, body = follow(target = target))

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
            installModule(TestRemoteActors())

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

    @Test
    fun `Follow を受けたら相手の inbox に Accept を返す`() =
        testApplication {
            val delivery = TestDelivery()
            installModule(delivery = delivery)

            postInbox(body = follow())

            val sent = delivery.delivered.single()
            assertEquals(TestRemoteActor.INBOX, sent.inbox)
            assertEquals(fixedActor, sent.sender.actorId)

            val accept = AppJson.parseToJsonElement(sent.body) as JsonObject
            assertEquals("Accept", accept["type"]?.jsonPrimitive?.content)
            assertEquals(fixedActor, accept["actor"]?.jsonPrimitive?.content)

            // 受け取った Follow を丸ごと入れる。id だけだと突き合わせられない実装がある
            val embedded = accept["object"] as JsonObject
            assertEquals("Follow", embedded["type"]?.jsonPrimitive?.content)
            assertEquals("https://remote.example/activities/1", embedded["id"]?.jsonPrimitive?.content)
        }

    @Test
    fun `Accept の id はアクターごとに一意になる`() =
        testApplication {
            val delivery = TestDelivery()
            installModule(delivery = delivery)

            postInbox(body = follow())
            postInbox(body = follow())

            val ids =
                delivery.delivered.map {
                    (AppJson.parseToJsonElement(it.body) as JsonObject)["id"]?.jsonPrimitive?.content
                }

            assertEquals(2, ids.size)
            assertEquals(2, ids.toSet().size)
            assertTrue(ids.all { it != null && it.startsWith("$fixedActor#accepts/follows/") }, "$ids")
        }

    @Test
    fun `宛先の違う Follow には Accept を返さない`() =
        testApplication {
            val delivery = TestDelivery()
            installModule(delivery = delivery)

            // 署名も actor も正しいが、フォローしようとしている相手が別のアクター
            val response = postInbox(body = follow(target = "https://${TestLocalActor.DOMAIN}/users/other"))

            assertEquals(HttpStatusCode.Accepted, response.status)
            assertTrue(delivery.delivered.isEmpty(), "${delivery.delivered}")
        }

    @Test
    fun `Follow 以外には Accept を返さない`() =
        testApplication {
            val delivery = TestDelivery()
            installModule(delivery = delivery)

            val undo =
                """
                {"id":"https://remote.example/activities/2","type":"Undo",
                 "actor":"${TestRemoteActor.ACTOR_ID}","object":"https://remote.example/activities/1"}
                """.trimIndent().toByteArray()

            val response = postInbox(body = undo)

            assertEquals(HttpStatusCode.Accepted, response.status)
            assertTrue(delivery.delivered.isEmpty(), "${delivery.delivered}")
        }

    @Test
    fun `相手の inbox を引けなくても202で返す`() =
        testApplication {
            val delivery = TestDelivery()
            // 鍵は引けるが inbox が分からない相手
            installModule(remoteActors = TestRemoteActor.remoteActors(inbox = null), delivery = delivery)

            val response = postInbox(body = follow())

            // 送れなかったことを 5xx で伝えると、相手は同じ Follow を送り直し続ける
            assertEquals(HttpStatusCode.Accepted, response.status)
            assertTrue(delivery.delivered.isEmpty(), "${delivery.delivered}")
        }
}
