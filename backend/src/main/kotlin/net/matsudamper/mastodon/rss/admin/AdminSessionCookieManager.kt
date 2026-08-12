package net.matsudamper.mastodon.rss.admin

import io.ktor.http.CookieEncoding
import io.ktor.server.application.ApplicationCall
import io.ktor.util.date.GMTDate

/**
 * セッション Cookie の読み書き。
 * 発行と失効で属性がずれると別の Cookie として扱われるので、1 箇所にまとめる
 */
class AdminSessionCookieManager(
    private val call: ApplicationCall,
    private val secure: Boolean,
) {
    fun token(): String? = call.request.cookies[COOKIE_NAME, ENCODING]

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
            name = COOKIE_NAME,
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

    companion object {
        const val COOKIE_NAME: String = "admin_session"

        private val ENCODING = CookieEncoding.RAW
    }
}
