package net.matsudamper.mastodon.rss.webpage

/**
 * カードの説明文にするため、投稿の HTML を 1 行の平文にする。
 *
 * 投稿の HTML は取り込み時に組み立てた単純なものなので、タグを落として文字参照を戻すだけで足りる。
 */
internal object HtmlText {
    private const val MAX_DESCRIPTION_CODE_POINTS = 200
    private val lineBreakTag = Regex("""<(?:br|/p|/div|/li)\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val tag = Regex("""<[^>]*>""")
    private val characterReference = Regex("""&(#[0-9]+|#[xX][0-9A-Fa-f]+|amp|lt|gt|quot|apos|nbsp);""")
    private val whitespace = Regex("""\s+""")

    fun description(html: String): String {
        val text = html
            .replace(lineBreakTag, " ")
            .replace(tag, "")
            .replace(characterReference) { decode(it.groupValues[1]) }
            .replace(whitespace, " ")
            .trim()
        if (text.codePointCount(0, text.length) <= MAX_DESCRIPTION_CODE_POINTS) return text
        return text.substring(0, text.offsetByCodePoints(0, MAX_DESCRIPTION_CODE_POINTS - 1)) + "…"
    }

    private fun decode(reference: String): String =
        when (reference) {
            "amp" -> "&"

            "lt" -> "<"

            "gt" -> ">"

            "quot" -> "\""

            "apos" -> "'"

            "nbsp" -> " "

            else -> {
                val codePoint = if (reference.startsWith("#x", ignoreCase = true)) {
                    reference.drop(2).toIntOrNull(16)
                } else {
                    reference.drop(1).toIntOrNull()
                }
                if (codePoint != null && Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint)) else ""
            }
        }
}
