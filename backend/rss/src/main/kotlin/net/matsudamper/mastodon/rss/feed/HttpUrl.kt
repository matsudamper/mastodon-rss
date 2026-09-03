package net.matsudamper.mastodon.rss.feed

import java.net.URI

object HttpUrl {
    /**
     * 表示や外部リンクに渡してよい http / https の URL だけを残す。
     *
     * @param baseUrl 相対 URL を解決するときの基準。フィードの URL を渡す
     */
    fun sanitize(
        url: String?,
        baseUrl: String? = null,
    ): String? {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        val resolved =
            if (baseUrl != null) {
                runCatching { URI(baseUrl).resolve(trimmed).toString() }.getOrDefault(trimmed)
            } else {
                trimmed
            }

        val uri = runCatching { URI(resolved) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null

        return resolved
    }
}
