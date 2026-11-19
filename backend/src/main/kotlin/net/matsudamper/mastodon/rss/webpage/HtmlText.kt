package net.matsudamper.mastodon.rss.webpage

/**
 * カードの説明文にするため、1 行の平文にする。
 *
 * 投稿の HTML は取り込み時に組み立てた単純なものなので、タグを落として文字参照を戻すだけで足りる。
 */
internal object HtmlText {
    private const val MAX_DESCRIPTION_CODE_POINTS = 200
    private val lineBreakTag = Regex("""<(?:br|/p|/div|/li)\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val tag = Regex("""<[^>]*>""")
    private val characterReference = Regex("""&(#[0-9]+|#[xX][0-9A-Fa-f]+|amp|lt|gt|quot|apos|nbsp);""")
    private val whitespace = Regex("""\s+""")

    /**
     * 投稿の本文のような HTML から作る
     */
    fun description(html: String): String =
        plainDescription(
            html
                .replace(lineBreakTag, " ")
                .replace(tag, "")
                .replace(characterReference) { decode(it.groupValues[1]) },
        )

    /**
     * アカウントの説明文のような、平文のまま保存しているものから作る。
     * HTML として読むと、書いたとおりの `&amp;` や `<b>` が変わったり消えたりする
     */
    fun plainDescription(text: String): String {
        val oneLine = text.replace(whitespace, " ").trim()
        if (oneLine.codePointCount(0, oneLine.length) <= MAX_DESCRIPTION_CODE_POINTS) return oneLine
        return oneLine.substring(0, oneLine.offsetByCodePoints(0, MAX_DESCRIPTION_CODE_POINTS - 1)) + "…"
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
