package net.matsudamper.mastodon.rss.admin

import io.ktor.server.application.ApplicationCall

class AdminNotLoggedInException : RuntimeException("ログインしていない")

/**
 * ログインした人だけが叩けるフィールドはこれを通す。
 *
 * `session` と `login` は通さない。ログインしているかを聞く口とログインする口なので、
 * ログインを要求すると入れなくなる。
 *
 * 認可をエンドポイントではなくフィールドごとに見る決まりなので、守る対象が増えたときに
 * 足すのはここを通す 1 行だけになる。
 */
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
