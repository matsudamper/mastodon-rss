package net.matsudamper.mastodon.rss.admin

import io.ktor.server.application.ApplicationCall

class AdminNotLoggedInException : RuntimeException("ログインしていない")

/** ログインが要るフィールドはこれを通す。`session` と `login` は通さない */
@Suppress("unused")
fun <T> AdminSessions.requireLoggedIn(
    call: ApplicationCall,
    block: () -> T,
): T {
    if (!isValid(call.sessionToken())) {
        throw AdminNotLoggedInException()
    }
    return block()
}
