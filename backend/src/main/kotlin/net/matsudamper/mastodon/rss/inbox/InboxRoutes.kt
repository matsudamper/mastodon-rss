package net.matsudamper.mastodon.rss.inbox

import kotlinx.io.readByteArray
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.uri
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.ActorUrls
import net.matsudamper.kotpub.inbox.InboxEndpoint
import net.matsudamper.kotpub.inbox.InboxHandling
import net.matsudamper.kotpub.inbox.InboxResult
import net.matsudamper.kotpub.inbox.InboxService
import net.matsudamper.kotpub.inbox.IncomingInboxRequest
import net.matsudamper.mastodon.rss.http.KtorRequestHeaders
import net.matsudamper.mastodon.rss.http.respondEndpoint
import org.slf4j.LoggerFactory

internal fun Route.inboxRoutes(
    directory: ActorDirectory,
    service: InboxService,
) {
    val endpoint = InboxEndpoint(directory, service)
    post("/users/{username}/inbox") {
        val received = endpoint.receiveForAccount(username = call.parameters["username"], request = call.incomingInboxRequest())
        logInboxResult(received.result)
        call.respondEndpoint(received.response)
    }

    post(ActorUrls.SHARED_INBOX_PATH) {
        val received = endpoint.receiveShared(call.incomingInboxRequest())
        logInboxResult(received.result)
        call.respondEndpoint(received.response)
    }
}

private val inboxLogger = LoggerFactory.getLogger("net.matsudamper.mastodon.rss.inbox.Inbox")

/**
 * kotpub は受け付けなかった理由をログに残さずに結果で返すので、こちらで残す
 *
 * @param result 宛先のアカウントが無いなど、処理まで進まなかったときは null
 */
private fun logInboxResult(result: InboxResult?) {
    when (result) {
        null -> Unit

        is InboxResult.Accepted -> {
            val handling = result.handling
            if (handling is InboxHandling.Ignored) inboxLogger.info("inbox で受け付けなかった: ${handling.reason}")
        }

        is InboxResult.Unauthorized -> inboxLogger.warn("inbox の署名を拒否した: ${result.reason}")

        is InboxResult.BadRequest -> inboxLogger.warn("inbox のボディを読めなかった: ${result.reason}")

        is InboxResult.TemporarilyUnavailable -> inboxLogger.warn("inbox を処理できなかったので送り直してもらう", result.cause)
    }
}

private fun ApplicationCall.incomingInboxRequest(): IncomingInboxRequest =
    IncomingInboxRequest(
        method = request.httpMethod.value,
        requestTarget = request.uri,
        headers = KtorRequestHeaders(request.headers),
        readBody = { maxBytes -> receiveChannel().readRemaining(maxBytes + 1L).readByteArray() },
    )
