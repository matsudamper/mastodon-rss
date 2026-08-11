package net.matsudamper.mastodon.rss.admin

import io.ktor.http.CookieEncoding
import io.ktor.server.application.ApplicationCall
import io.ktor.util.date.GMTDate

// セッションを Cookie で持ち回る部分。

/** トークンは URL-safe Base64 なので、エンコードするものが無い */
private val COOKIE_ENCODING = CookieEncoding.RAW

/** リクエストが持っているトークン。無ければ null */
fun ApplicationCall.sessionToken(): String? = request.cookies[AdminSessions.COOKIE_NAME, COOKIE_ENCODING]

/**
 * セッション Cookie を返す。`HttpOnly` は script を差し込まれたときに
 * 持ち出されないため、`SameSite=Strict` は他所のページから叩かれても付かないため。
 *
 * @param secure http では Cookie が保存されなくなるので、手元で試すときだけ外す
 */
fun ApplicationCall.appendSessionCookie(
    token: String,
    maxAgeSeconds: Long,
    secure: Boolean,
) {
    response.cookies.append(
        name = AdminSessions.COOKIE_NAME,
        value = token,
        encoding = COOKIE_ENCODING,
        maxAge = maxAgeSeconds,
        path = "/",
        secure = secure,
        httpOnly = true,
        extensions = mapOf("SameSite" to "Strict"),
    )
}

/**
 * セッション Cookie を消す。過去の期限を付けた同じ Cookie を返すことでしかないので、
 * `Path` と `Secure` は発行したときと揃える。ずれると別の Cookie として扱われる。
 */
fun ApplicationCall.expireSessionCookie(secure: Boolean) {
    response.cookies.append(
        name = AdminSessions.COOKIE_NAME,
        value = "",
        encoding = COOKIE_ENCODING,
        // 過ぎている期限を渡すのがブラウザに消させる唯一の手段
        expires = GMTDate.START,
        path = "/",
        secure = secure,
        httpOnly = true,
        extensions = mapOf("SameSite" to "Strict"),
    )
}
