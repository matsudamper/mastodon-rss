package net.matsudamper.mastodon.rss.ratelimit

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.TestServerEnv
import net.matsudamper.mastodon.rss.module
import net.matsudamper.mastodon.rss.testDependencies

// 署名を拒否した送信元を inbox の手前で止める。
// 止まらないと、通らない署名を送り続けるだけで相手のサーバーへの取得を起こせる。
class InboxCoolOffPluginTest {
    private val inboxPath = "/users/${TestServerEnv.USERNAME}/inbox"

    private fun FakeRepositories.withAccount(): FakeRepositories =
        apply { accounts.add(username = TestServerEnv.USERNAME, createdAt = Instant.now()) }

    @Test
    fun `署名を拒否された送信元は次から通さない`() =
        testApplication {
            application { module(testDependencies(repositories = FakeRepositories().withAccount())) }

            val first = client.post(inboxPath) { header(CLIENT_IP_HEADER, "203.0.113.1") }
            val second = client.post(inboxPath) { header(CLIENT_IP_HEADER, "203.0.113.1") }

            assertEquals(HttpStatusCode.Unauthorized, first.status)
            assertEquals(HttpStatusCode.TooManyRequests, second.status)
        }

    @Test
    fun `止めるのは拒否された送信元だけ`() =
        testApplication {
            application { module(testDependencies(repositories = FakeRepositories().withAccount())) }

            client.post(inboxPath) { header(CLIENT_IP_HEADER, "203.0.113.1") }

            val other = client.post(inboxPath) { header(CLIENT_IP_HEADER, "203.0.113.2") }

            assertEquals(HttpStatusCode.Unauthorized, other.status)
        }

    @Test
    fun `inbox 以外は止めない`() =
        testApplication {
            application { module(testDependencies(repositories = FakeRepositories().withAccount())) }

            client.post(inboxPath) { header(CLIENT_IP_HEADER, "203.0.113.1") }

            // 止めるのは inbox だけ。他のエンドポイントは同じ送信元でも通る
            val healthz = client.get("/healthz") { header(CLIENT_IP_HEADER, "203.0.113.1") }

            assertEquals(HttpStatusCode.OK, healthz.status)
        }

    private companion object {
        const val CLIENT_IP_HEADER = "CF-Connecting-IP"
    }
}
