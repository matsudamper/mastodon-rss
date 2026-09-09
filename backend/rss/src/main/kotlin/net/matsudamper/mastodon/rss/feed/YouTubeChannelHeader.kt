package net.matsudamper.mastodon.rss.feed

/**
 * YouTube のチャンネルページに埋め込まれたバナー画像を読む。
 *
 * YouTube のフィードはヘッダーになる画像を持たないので、チャンネルページの HTML から拾う。
 * ページに埋め込まれた JSON の形は公開された仕様ではないため、鍵の並びが見つからなければ
 * 何も返さない。新しい `imageBannerViewModel` を先に見て、無ければ以前の
 * `c4TabbedHeaderRenderer` を見る
 */
object YouTubeChannelHeader {
    fun fromPageHtml(html: String): String? =
        bannerUrl(
            html = html,
            keys = listOf("\"imageBannerViewModel\"", "\"sources\""),
        ) ?: bannerUrl(
            html = html,
            keys = listOf("\"c4TabbedHeaderRenderer\"", "\"banner\"", "\"thumbnails\""),
        )

    private fun bannerUrl(
        html: String,
        keys: List<String>,
    ): String? {
        var index = 0
        keys.forEachIndexed { position, key ->
            val found = html.indexOf(key, startIndex = index)
            if (found < 0) return null
            if (position > 0 && found - index > MAX_KEY_DISTANCE) return null
            index = found + key.length
        }

        val arrayStart = html.indexOf('[', startIndex = index)
        if (arrayStart < 0 || arrayStart - index > MAX_KEY_DISTANCE) return null
        val arrayEnd = html.indexOf(']', startIndex = arrayStart + 1)
        if (arrayEnd < 0 || arrayEnd - arrayStart > MAX_SOURCES_LENGTH) return null

        // 同じバナーが小さい順に並ぶので、最後の 1 つが一番大きい
        return URL_IN_JSON
            .findAll(html.substring(arrayStart + 1, arrayEnd))
            .map { match -> unescapeJsonUrl(match.groupValues[1]) }
            .filter { url -> url.startsWith("https://") }
            .lastOrNull()
    }

    /**
     * JSON の文字列に入っている URL の書き方を戻す。
     *
     * ページの JSON は HTML に埋め込む都合で `&` と `/` をエスケープしている
     */
    private fun unescapeJsonUrl(value: String): String =
        value
            .replace("\\u0026", "&")
            .replace("\\/", "/")

    private val URL_IN_JSON = Regex(""""url"\s*:\s*"((?:[^"\\]|\\.)*)"""")

    /**
     * 鍵から次の鍵までに許す文字数。
     *
     * ページ全体は数 MB あり、離れた場所にたまたま同じ鍵が並んでいることがある。
     * 続きとして読める距離に区切って、別の場所の URL を拾わないようにする
     */
    private const val MAX_KEY_DISTANCE = 64 * 1024

    /** バナーの並び 1 つ分に許す文字数。ここを超えたら別の場所を読んでいる */
    private const val MAX_SOURCES_LENGTH = 64 * 1024
}
