package net.matsudamper.mastodon.rss.http

import java.net.URI

/**
 * 絶対 URL のうち、宛先を決める部分だけ。ホストの無いものは読めない扱いにする
 */
internal class HttpUrl private constructor(
    val scheme: String,
    val host: String,
    /**
     * 書かれていなければ -1
     */
    val port: Int,
    val rawPath: String,
    val rawQuery: String?,
) {
    val isHttps: Boolean get() = scheme == HTTPS

    fun isSameHost(other: HttpUrl): Boolean = host.equals(other.host, ignoreCase = true)

    companion object {
        private const val HTTPS = "https"

        fun parse(raw: String): HttpUrl? {
            // パスやクエリに percent-encoding されていない非 ASCII 文字があると、rawPath はそれを
            // そのまま返す。送るときは UTF-8 で符号化されるので、署名する綴りもそちらに揃える
            val uri = runCatching { URI(URI(raw).toASCIIString()) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase() ?: return null
            val host = uri.host?.takeIf { it.isNotEmpty() } ?: return null

            return HttpUrl(
                scheme = scheme,
                host = host,
                port = uri.port,
                rawPath = uri.rawPath.orEmpty(),
                rawQuery = uri.rawQuery,
            )
        }

        fun parseHttps(raw: String): HttpUrl? = parse(raw)?.takeIf { it.isHttps }
    }
}
