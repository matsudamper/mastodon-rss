package net.matsudamper.mastodon.rss.linkpreview

import java.net.URI

/**
 * HTML から OGP とリンクを抜き出す。
 *
 * 見るのはタグ 1 つずつの属性だけで、HTML パーサとしては簡易なもの。
 */
object OgpParser {
    private val metaTag = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val anchorTag = Regex("""<a\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val titleTag = Regex("""<title\b[^>]*>([^<]*)</title>""", RegexOption.IGNORE_CASE)
    private val attribute = Regex("""([A-Za-z_:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
    private val charsetAttribute = Regex("""charset\s*=\s*["']?([A-Za-z0-9_.:-]+)""", RegexOption.IGNORE_CASE)
    private val characterReference = Regex("""&(#[0-9]+|#[xX][0-9A-Fa-f]+|amp|lt|gt|quot|apos);""")

    /**
     * 本文の `<a href>` を出てくる順に、重複を除いて返す。http と https のものだけ
     */
    fun links(html: String): List<String> =
        anchorTag.findAll(html)
            .mapNotNull { match -> attributes(match.value)["href"] }
            .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            .distinct()
            .toList()

    /**
     * @param pageUrl 取ってきたページの URL。相対で書かれた画像の基準にする
     */
    fun parse(
        html: String,
        pageUrl: String,
    ): Ogp {
        val metas = metaTag.findAll(html)
            .map { attributes(it.value) }
            .mapNotNull { attributes ->
                val key = (attributes["property"] ?: attributes["name"])?.lowercase() ?: return@mapNotNull null
                val content = attributes["content"]?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                key to content
            }
            // 同じ property が複数あれば先頭を使う
            .distinctBy { it.first }
            .toMap()

        val title = metas["og:title"]
            ?: metas["twitter:title"]
            ?: titleTag.find(html)?.groupValues?.get(1)?.let { unescape(it).trim() }?.takeIf { it.isNotEmpty() }
        val imageUrl = (metas["og:image:secure_url"] ?: metas["og:image"] ?: metas["twitter:image"])
            ?.let { resolveHttpUrl(base = pageUrl, url = it) }

        return Ogp(
            title = title,
            siteName = metas["og:site_name"],
            imageUrl = imageUrl,
        )
    }

    /**
     * `<meta charset>` と `<meta http-equiv="Content-Type">` の文字コード。無ければ null
     */
    fun declaredCharset(html: String): String? =
        metaTag.findAll(html)
            .map { attributes(it.value) }
            .firstNotNullOfOrNull { attributes ->
                attributes["charset"]
                    ?: attributes["content"]
                        ?.takeIf { attributes["http-equiv"].equals("content-type", ignoreCase = true) }
                        ?.let { charsetAttribute.find(it)?.groupValues?.get(1) }
            }

    private fun attributes(tag: String): Map<String, String> =
        attribute.findAll(tag)
            .map { match ->
                val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
                match.groupValues[1].lowercase() to unescape(value)
            }
            .distinctBy { it.first }
            .toMap()

    private fun resolveHttpUrl(
        base: String,
        url: String,
    ): String? {
        val resolved = runCatching { URI(base).resolve(url.trim()) }.getOrNull() ?: return null
        val scheme = resolved.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        return resolved.toString()
    }

    private fun unescape(value: String): String =
        characterReference.replace(value) { match ->
            when (val name = match.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                else -> {
                    val codePoint = if (name[1] == 'x' || name[1] == 'X') {
                        name.substring(2).toIntOrNull(16)
                    } else {
                        name.substring(1).toIntOrNull()
                    }
                    if (codePoint != null && Character.isValidCodePoint(codePoint)) {
                        String(Character.toChars(codePoint))
                    } else {
                        match.value
                    }
                }
            }
        }

    data class Ogp(
        val title: String?,
        val siteName: String?,
        val imageUrl: String?,
    )
}
