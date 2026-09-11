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
            keys = listOf("\"pageHeaderViewModel\"", "\"banner\"", "\"imageBannerViewModel\"", "\"sources\""),
            // pageHeaderViewModel -> banner だけ、この入れ物の階層の分だけ離れている
            keyDistances = listOf(MAX_PAGE_HEADER_DISTANCE, MAX_KEY_DISTANCE, MAX_KEY_DISTANCE),
        ) ?: bannerUrl(
            html = html,
            keys = listOf("\"c4TabbedHeaderRenderer\"", "\"banner\"", "\"thumbnails\""),
            keyDistances = listOf(MAX_LEGACY_KEY_DISTANCE, MAX_LEGACY_KEY_DISTANCE),
        )

    /**
     * 先頭の鍵が見つかっても、その出現がバナーの実データとは限らない
     * （[MAX_KEY_DISTANCE] のコメント参照）。1 か所で鍵の並びが崩れて諦めるのではなく、
     * 先頭の鍵の次の出現から探し直す
     */
    private fun bannerUrl(
        html: String,
        keys: List<String>,
        keyDistances: List<Int>,
    ): String? {
        var searchFrom = 0
        while (true) {
            val head = html.indexOf(keys.first(), startIndex = searchFrom)
            if (head < 0) return null
            bannerUrlAt(html, keys, keyDistances, head)?.let { return it }
            searchFrom = head + 1
        }
    }

    private fun bannerUrlAt(
        html: String,
        keys: List<String>,
        keyDistances: List<Int>,
        head: Int,
    ): String? {
        var index = head + keys.first().length
        keys.drop(1).forEachIndexed { hop, key ->
            val found = html.indexOf(key, startIndex = index)
            if (found < 0 || found - index > keyDistances[hop]) return null
            index = found + key.length
        }

        val arrayDistance = keyDistances.last()
        val arrayStart = html.indexOf('[', startIndex = index)
        if (arrayStart < 0 || arrayStart - index > arrayDistance) return null
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
     * 新形式の `banner` → `imageBannerViewModel` → `sources` → 配列の開始、各区間に許す文字数。
     *
     * `imageBannerViewModel` という名前だけは、実データより手前のプリロード対象一覧
     * （`preloadMessageNames` の文字列配列）にも出てくる。そこには `"sources"` を
     * 伴わないが、探す範囲が広いと数万文字先の無関係な `"sources"`（動画サムネイル等）
     * まで拾ってしまい、バナーではなく別の画像を返してしまう。実データでは鍵同士が
     * 数文字〜十数文字しか離れていないので、続きとして読める狭さに絞る
     */
    private const val MAX_KEY_DISTANCE = 256

    /**
     * `pageHeaderViewModel` から `banner` までに許す文字数。
     *
     * 間にチャンネル名やアバターなど他の項目を挟むので、[MAX_KEY_DISTANCE] ほどは
     * 狭められない。実データでは 1 万 2 千文字前後離れているので、余裕を見て 32KB とする。
     * この鍵を先頭に置くのは、ページ内の無関係な場所にたまたま完結した
     * `banner`/`imageBannerViewModel`/`sources` の並びがあっても、
     * そちらを拾わないようにするため
     */
    private const val MAX_PAGE_HEADER_DISTANCE = 32 * 1024

    /**
     * 旧形式（`c4TabbedHeaderRenderer`）の鍵から次の鍵までに許す文字数。
     *
     * 実データでは `channelId`・`title`・`navigationEndpoint`・`avatar` を挟んで
     * `banner` まで数百文字離れるため、[MAX_KEY_DISTANCE] ほど狭めると
     * 常に見つからなくなる。誤検出の実例が無い形式なので、元の広さのまま残す
     */
    private const val MAX_LEGACY_KEY_DISTANCE = 64 * 1024

    /**
     * バナーの並び 1 つ分に許す文字数。ここを超えたら別の場所を読んでいる
     */
    private const val MAX_SOURCES_LENGTH = 64 * 1024
}
