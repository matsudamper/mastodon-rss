package net.matsudamper.mastodon.rss.admin

import io.ktor.http.CookieEncoding
import io.ktor.server.application.ApplicationCall
import io.ktor.util.date.GMTDate

/** 発行と失効で属性がずれると別の Cookie として扱われるので、1 箇所にまとめる */
class AdminSessionCookie(
    private val call: ApplicationCall,
    private val secure: Boolean,
) {
    fun token(): String? = call.request.cookies[AdminSessions.COOKIE_NAME, ENCODING]

    fun append(
        token: String,
        maxAgeSeconds: Long,
    ) {
        append(value = token, maxAge = maxAgeSeconds, expires = null)
    }

    fun expire() {
        append(value = "", maxAge = null, expires = GMTDate.START)
    }

    private fun append(
        value: String,
        maxAge: Long?,
        expires: GMTDate?,
    ) {
        call.response.cookies.append(
            name = AdminSessions.COOKIE_NAME,
            value = value,
            encoding = ENCODING,
            maxAge = maxAge,
            expires = expires,
            path = "/",
            secure = secure,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Strict"),
        )
    }

    private companion object {
        val ENCODING = CookieEncoding.RAW
    }
}
