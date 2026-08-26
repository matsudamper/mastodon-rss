package net.matsudamper.mastodon.rss.actor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.matsudamper.mastodon.rss.TestActorKey
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.activitypub.Actor
import net.matsudamper.mastodon.rss.json.AppJson

// アカウント発見の 2 ホップ目。Mastodon はここの JSON からプロフィールと公開鍵を作る。
class ActorRoutesTest {
    private fun ApplicationTestBuilder.installModule() {
        application {
            routing {
                actorRoutes(TestLocalActor.directory, TestActorKey.value)
            }
        }
    }

    @Test
    fun `Actor の JSON が返る`() =
        testApplication {
            installModule()

            val response = client.get("/users/admin")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("application/activity+json", response.contentType()?.withoutParameters()?.toString())

            val actor = AppJson.decodeFromString(Actor.serializer(), response.bodyAsText())
            assertEquals("https://example.com/users/admin", actor.id)
            assertEquals("Service", actor.type)
            assertEquals("admin", actor.preferredUsername)
            assertEquals("https://example.com/users/admin/inbox", actor.inbox)
            assertEquals("https://example.com/users/admin/outbox", actor.outbox)
            assertEquals("https://example.com/users/admin/collections/featured", actor.featured)
            assertEquals(true, actor.showFeatured)
            assertEquals("https://example.com/users/admin/followers", actor.followers)
            assertEquals("https://example.com/users/admin/following", actor.following)
        }

    @Test
    fun `context に activitystreams と security が入る`() =
        testApplication {
            installModule()

            val body = client.get("/users/admin").bodyAsText()

            // @context は @SerialName で出す必要がある。素の Kotlin 識別子では書けない
            val expected =
                """"@context":["https://www.w3.org/ns/activitystreams",""" +
                    """"https://w3id.org/security/v1"]"""
            assertTrue(body.contains(expected))
        }

    @Test
    fun `公開鍵は秘密鍵から導いたものが入る`() =
        testApplication {
            installModule()

            val actor = AppJson.decodeFromString(Actor.serializer(), client.get("/users/admin").bodyAsText())

            assertEquals("https://example.com/users/admin#main-key", actor.publicKey.id)
            assertEquals("https://example.com/users/admin", actor.publicKey.owner)
            assertEquals(TestActorKey.value.publicKeyPem, actor.publicKey.publicKeyPem)
            // PKCS#1 の BEGIN RSA PUBLIC KEY だと Mastodon が読めない
            assertTrue(actor.publicKey.publicKeyPem.startsWith("-----BEGIN PUBLIC KEY-----"))
        }

    @Test
    fun `Accept が ld+json ならその Content-Type で返す`() =
        testApplication {
            installModule()

            // Mastodon は profile パラメータ付きで送ってくる
            val accept = """application/ld+json; profile="https://www.w3.org/ns/activitystreams""""
            val response = client.get("/users/admin") { header(HttpHeaders.Accept, accept) }

            assertEquals("application/ld+json", response.contentType()?.withoutParameters()?.toString())
        }

    @Test
    fun `Accept が無くても activity+json で返す`() =
        testApplication {
            installModule()

            // Accept を送らない相手に application/json を返すとアクターとして認識されない
            val response = client.get("/users/admin") { header(HttpHeaders.Accept, "*/*") }

            assertEquals("application/activity+json", response.contentType()?.withoutParameters()?.toString())
        }

    @Test
    fun `知らないユーザー名は404`() =
        testApplication {
            installModule()

            assertEquals(HttpStatusCode.NotFound, client.get("/users/other").status)
        }

    @Test
    fun `保存されているアカウントもその名前で返る`() =
        testApplication {
            installModule()

            val path = "/users/${TestLocalActor.STORED_USERNAME}"
            val actor = AppJson.decodeFromString(Actor.serializer(), client.get(path).bodyAsText())

            assertEquals("https://example.com$path", actor.id)
            assertEquals(TestLocalActor.STORED_USERNAME, actor.preferredUsername)
            assertEquals("https://example.com$path/inbox", actor.inbox)
            assertEquals("https://example.com$path#main-key", actor.publicKey.id)
            // 鍵はまだアクターごとに持っていない
            assertEquals(TestActorKey.value.publicKeyPem, actor.publicKey.publicKeyPem)
        }
}
