package net.matsudamper.mastodon.rss.feed

import java.net.URI

/**
 * Web ページが名乗っている favicon の URL を決める。
 *
 * ネットワークには触らない。HTML の取得は HTTP クライアントを持つ `:backend` 側で行い、
 * ここでは `<link rel="icon" href="...">` を読んで絶対 URL にするだけ。
 */
object FaviconResolver {
    private val linkTag = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val attribute =
        Regex("""([^\s=/>]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")

    /**
     * ページの HTML から favicon を探す。
     *
     * `shortcut icon` は rel のトークンに `icon` を含むので同じ扱いになる。
     * 宣言が無ければ、ブラウザが慣例的に見るオリジン直下の `/favicon.ico` を返す。
     */
    fun resolve(pageUrl: String, html: String): String? {
        val base = httpUri(pageUrl) ?: return null

        for (tag in linkTag.findAll(html)) {
            val attributes = attributesOf(tag.value)
            val rel = attributes["rel"]
                ?.split(Regex("\\s+"))
                ?.map { it.lowercase() }
                .orEmpty()
            if ("icon" !in rel) continue

            val href = attributes["href"]?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            resolveHttp(base, decodeAttribute(href))?.let { return it.toString() }
        }

        return defaultUrl(base)
    }

    /** ページを取得できなかった場合にも試せる、オリジン直下の favicon */
    fun defaultUrl(pageUrl: String): String? = httpUri(pageUrl)?.let(::defaultUrl)

    private fun defaultUrl(pageUrl: URI): String =
        URI(
            pageUrl.scheme,
            null,
            pageUrl.host,
            pageUrl.port,
            "/favicon.ico",
            null,
            null,
        ).toString()

    private fun attributesOf(tag: String): Map<String, String> =
        buildMap {
            for (match in attribute.findAll(tag)) {
                val name = match.groupValues[1].lowercase()
                val value = match.groupValues.drop(2).firstOrNull { it.isNotEmpty() }.orEmpty()
                putIfAbsent(name, value)
            }
        }

    private fun resolveHttp(base: URI, value: String): URI? =
        runCatching { base.resolve(value) }
            .getOrNull()
            ?.takeIf { uri ->
                uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
            }

    private fun httpUri(value: String): URI? =
        runCatching { URI(value) }
            .getOrNull()
            ?.takeIf { uri ->
                uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
            }

    private fun decodeAttribute(value: String): String =
        value
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&#38;", "&", ignoreCase = true)
            .replace("&#x26;", "&", ignoreCase = true)
}
