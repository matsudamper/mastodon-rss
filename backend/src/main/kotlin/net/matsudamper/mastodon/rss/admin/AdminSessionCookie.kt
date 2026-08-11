package net.matsudamper.mastodon.rss.admin

import io.ktor.http.CookieEncoding
import io.ktor.server.application.ApplicationCall
import io.ktor.util.date.GMTDate

private val COOKIE_ENCODING = CookieEncoding.RAW

fun ApplicationCall.sessionToken(): String? = request.cookies[AdminSessions.COOKIE_NAME, COOKIE_ENCODING]

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

/** `Path` と `Secure` は発行したときと揃える。ずれると別の Cookie として扱われる */
fun ApplicationCall.expireSessionCookie(secure: Boolean) {
    response.cookies.append(
        name = AdminSessions.COOKIE_NAME,
        value = "",
        encoding = COOKIE_ENCODING,
        expires = GMTDate.START,
        path = "/",
        secure = secure,
        httpOnly = true,
        extensions = mapOf("SameSite" to "Strict"),
    )
}
