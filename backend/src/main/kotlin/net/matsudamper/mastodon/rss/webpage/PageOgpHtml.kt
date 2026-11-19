package net.matsudamper.mastodon.rss.webpage

/**
 * 画面の入口の HTML に、他のサービスに貼ったときのカードの meta を足す。
 *
 * カードを作る側は JavaScript を動かさないので、配信する HTML に最初から書いておく。
 */
internal object PageOgpHtml {
    private val headEnd = Regex("""</head\s*>""", RegexOption.IGNORE_CASE)
    private val titleElement = Regex("""<title\b[^>]*>.*?</title\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /**
     * @return `</head>` が無ければ [indexHtml] のまま
     */
    fun inject(
        indexHtml: String,
        ogp: PageOgp,
    ): String {
        val headEndMatch = headEnd.find(indexHtml) ?: return indexHtml
        val metas = buildList {
            add(property("og:type", ogp.type.ogType))
            add(property("og:title", ogp.title))
            add(property("og:url", ogp.url))
            if (ogp.description != null) {
                add(property("og:description", ogp.description))
                add(name("description", ogp.description))
            }
            if (ogp.imageUrl != null) {
                add(property("og:image", ogp.imageUrl))
            }
            // アイコンしか出さないので、大きい画像のカードにはしない
            add(name("twitter:card", "summary"))
        }
        val injected = indexHtml.substring(0, headEndMatch.range.first) +
            metas.joinToString(separator = "") { "$it\n" } +
            indexHtml.substring(headEndMatch.range.first)
        return titleElement.replaceFirst(injected, Regex.escapeReplacement("<title>${escape(ogp.title)}</title>"))
    }

    private fun property(
        property: String,
        content: String,
    ): String = """<meta property="${escape(property)}" content="${escape(content)}">"""

    private fun name(
        name: String,
        content: String,
    ): String = """<meta name="${escape(name)}" content="${escape(content)}">"""

    private fun escape(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}
