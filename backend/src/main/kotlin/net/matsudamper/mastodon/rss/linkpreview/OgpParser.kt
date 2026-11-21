package net.matsudamper.mastodon.rss.linkpreview

import java.net.URI

/**
 * HTML から OGP とリンクを抜き出す。
 *
 * 見るのはタグ 1 つずつの属性だけで、HTML パーサとしては簡易なもの。
 */
object OgpParser {
    private val charsetAttribute = Regex("""charset\s*=\s*["']?([A-Za-z0-9_.:-]+)""", RegexOption.IGNORE_CASE)
    private val characterReference = Regex("""&(#[0-9]+|#[xX][0-9A-Fa-f]+|amp|lt|gt|quot|apos);""")

    /**
     * 本文の `<a href>` を出てくる順に、重複を除いて返す。http と https のものだけ
     */
    fun links(html: String): List<String> =
        startTags(html, "a")
            .mapNotNull { tag -> attributes(tag)["href"] }
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
        val metas = startTags(html, "meta")
            .map { attributes(it) }
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
            ?: titleText(html)?.let { unescape(it).trim() }?.takeIf { it.isNotEmpty() }
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
        startTags(html, "meta")
            .map { attributes(it) }
            .firstNotNullOfOrNull { attributes ->
                attributes["charset"]
                    ?: attributes["content"]
                        ?.takeIf { attributes["http-equiv"].equals("content-type", ignoreCase = true) }
                        ?.let { charsetAttribute.find(it)?.groupValues?.get(1) }
            }

    /**
     * `<name ...>` の開始タグを先頭から順に返す。
     *
     * 正規表現で `<meta\b[^>]*>` と書くと、`>` が無いまま `<meta` が並ぶ入力で
     * 出現位置ごとに末尾まで読み直し、入力の長さの 2 乗の時間がかかる。
     * 閉じる `>` が無ければ後ろのタグも閉じないので、そこで打ち切る
     */
    private fun startTags(
        html: String,
        name: String,
    ): Sequence<String> = startTagRanges(html, name).map { html.substring(it) }

    private fun startTagRanges(
        html: String,
        name: String,
    ): Sequence<IntRange> =
        sequence {
            val opening = "<$name"
            var from = 0
            while (true) {
                val start = html.indexOf(opening, startIndex = from, ignoreCase = true)
                if (start < 0) break
                val nameEnd = start + opening.length
                if (nameEnd < html.length && html[nameEnd].isTagNameChar()) {
                    from = nameEnd
                    continue
                }
                val end = html.indexOf('>', startIndex = nameEnd)
                if (end < 0) break
                yield(start..end)
                from = end + 1
            }
        }

    private fun Char.isTagNameChar(): Boolean = this == '_' || this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    private fun titleText(html: String): String? =
        startTagRanges(html, "title").firstNotNullOfOrNull { tag ->
            val textStart = tag.last + 1
            val textEnd = html.indexOf('<', startIndex = textStart)
            if (textEnd >= 0 && html.startsWith("</title>", startIndex = textEnd, ignoreCase = true)) {
                html.substring(textStart, textEnd)
            } else {
                null
            }
        }

    /**
     * `name="value"` と `name='value'` だけを読む。
     *
     * 正規表現で書くと、閉じない引用符や `=` の無い長い名前で出現位置ごとに末尾まで読み直す
     */
    private fun attributes(tag: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val unclosedQuotes = mutableSetOf<Char>()
        var index = 0
        while (index < tag.length) {
            if (!tag[index].isAttributeNameChar()) {
                index++
                continue
            }
            val nameEnd = tag.indexOfFirstFrom(index) { !it.isAttributeNameChar() }
            val name = tag.substring(index, nameEnd)
            index = nameEnd
            val equals = tag.indexOfFirstFrom(nameEnd) { !it.isWhitespace() }
            if (equals >= tag.length || tag[equals] != '=') continue
            val valueStart = tag.indexOfFirstFrom(equals + 1) { !it.isWhitespace() }
            val quote = tag.getOrNull(valueStart)?.takeIf { it == '"' || it == '\'' } ?: continue
            if (quote in unclosedQuotes) continue
            val valueEnd = tag.indexOf(quote, startIndex = valueStart + 1)
            if (valueEnd < 0) {
                unclosedQuotes += quote
                continue
            }
            result.putIfAbsent(name.lowercase(), unescape(tag.substring(valueStart + 1, valueEnd)))
            index = valueEnd + 1
        }
        return result
    }

    private fun Char.isAttributeNameChar(): Boolean = this in 'A'..'Z' || this in 'a'..'z' || this == '_' || this == ':' || this == '-'

    private inline fun String.indexOfFirstFrom(
        start: Int,
        predicate: (Char) -> Boolean,
    ): Int {
        var index = start
        while (index < length && !predicate(this[index])) index++
        return index
    }

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
