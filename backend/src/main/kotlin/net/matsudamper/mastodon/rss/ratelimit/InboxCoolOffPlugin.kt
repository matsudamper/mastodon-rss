package net.matsudamper.mastodon.rss.ratelimit

import java.time.Duration
import java.time.Instant
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import org.slf4j.LoggerFactory

/**
 * inbox の手前で、署名を拒否された送信元を止める。
 *
 * ルーティングではなくプラグインとして入れるのは、止めるのが ActivityPub の
 * 決まりではなく、こちらのサーバーの都合だから。`:backend:feature-mastodon` は
 * HTTP の前段も送信元も知らないままにする。
 */
fun Application.installInboxCoolOff(coolOff: InboxCoolOff) {
    val logger = LoggerFactory.getLogger("net.matsudamper.mastodon.rss.ratelimit.InboxCoolOff")

    install(
        createApplicationPlugin(name = "InboxCoolOff") {
            onCall { call ->
                if (!call.isInbox()) return@onCall

                val clientIp = call.clientIp()
                val until = coolOff.blockedUntil(clientIp) ?: return@onCall

                // 明ける時刻を秒で伝える。相手のサーバーは送り直してくるので、
                // ここで落としても正当な配信であれば後で届く
                val retryAfter = Duration.between(Instant.now(), until).seconds.coerceAtLeast(1)
                call.response.header(HttpHeaders.RetryAfter, retryAfter.toString())
                call.respondText("しばらく受け付けない", status = HttpStatusCode.TooManyRequests)
            }

            on(ResponseSent) { call ->
                if (!call.isInbox()) return@on
                if (call.response.status() != HttpStatusCode.Unauthorized) return@on

                val clientIp = call.clientIp()
                coolOff.rejected(clientIp)
                logger.info("署名を拒否したので inbox をしばらく受け付けない: $clientIp")
            }
        },
    )
}

/**
 * inbox への POST か。
 *
 * パスで見るのは、プラグインがルーティングより手前で動くため。
 * 綴りは `:backend:feature-mastodon` の `inboxRoutes` と揃える
 */
private fun ApplicationCall.isInbox(): Boolean =
    request.httpMethod == HttpMethod.Post && INBOX_PATH.matches(request.path())

/**
 * 送信元。
 *
 * ソケットの接続元は Cloudflare になるので、本当の送信元は `CF-Connecting-IP` から取る
 */
private fun ApplicationCall.clientIp(): String =
    request.headers[CLOUDFLARE_CLIENT_IP_HEADER]?.takeIf { it.isNotBlank() }
        ?: request.origin.remoteAddress

private val INBOX_PATH = Regex("^/users/[^/]+/inbox$")

private const val CLOUDFLARE_CLIENT_IP_HEADER = "CF-Connecting-IP"
