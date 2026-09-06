package net.matsudamper.mastodon.rss.feed

/**
 * 記事のリンク先の HTML から Open Graph protocol の値を読む。
 *
 * ネットワークは触らない。[YouTubeFeedResolver] と同じく、取得は HTTP クライアントを
 * 持つ `:backend` 側に任せて、ここは文字列を見るだけにする。
 *
 * HTML を組み立て直さずに `<meta>` だけを正規表現で拾う。相手のページは壊れた入れ子や
 * 閉じ忘れを含むことがあり、木にできないと何も読めないのでは取りこぼしが多すぎる。
 * `<meta>` は中に別のタグを持たないので、タグ 1 つの範囲だけ見れば属性は揃う。
 *
 * ただし `<script>` とコメントの中身はタグに見えてもタグではない。落とさずに探すと、
 * 中に書かれた `</head>` で打ち切ったり、中の `<meta>` を拾ったりする。
 */
object OpenGraph {
    /** 画像を探す順。前にあるものを優先する */
    private val imageKeys = listOf("og:image", "og:image:url", "og:image:secure_url")

    /** 属性を持つ `<meta ...>` 1 つぶん */
    private val metaTag = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)

    /** `name="value"` / `name='value'` / 引用符なしの `name=value` */
    private val attribute = Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""")

    private val headEnd = Regex("</head", RegexOption.IGNORE_CASE)

    /**
     * タグとして読んではいけない範囲。コメントと、中身が文字列として扱われる要素。
     * 閉じ忘れているものは落とせないが、その場合は元から木にもできない
     */
    private val ignoredRegions = Regex(
        """<!--.*?-->|<(script|style|template|noscript)\b[^>]*>.*?</\1\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val numericEntity = Regex("&#([xX][0-9A-Fa-f]+|[0-9]+);")

    private val namedEntities = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
    )

    /**
     * 画像の URL を返す。相対 URL のまま返すので、絶対化と http / https の確認は
     * 呼び出し側で [HttpUrl.sanitize] に通すこと。
     *
     * `og:image` を先に探し、無ければ `og:image:url` と `og:image:secure_url` を見る。
     * 後ろの 2 つは `og:image` の言い換えで、片方だけを書くページがある。
     *
     * 複数枚あるページでは最初の 1 枚を採る。OGP は先頭を代表の画像とする決まり。
     */
    fun imageUrl(html: String): String? {
        val scannable = ignoredRegions.replace(html, "")
        val head = scannable.substring(0, headEnd.find(scannable)?.range?.first ?: scannable.length)
        val images = mutableMapOf<String, String>()

        metaTag.findAll(head).forEach { tag ->
            val attributes = attributesOf(tag.value)
            // 相手のページは property と name のどちらでも書いてくる。
            // OGP の決まりは property だが、name で書くページも実際にある
            val key = (attributes["property"] ?: attributes["name"])?.lowercase() ?: return@forEach
            if (key !in imageKeys) return@forEach
            val content = attributes["content"]?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            images.putIfAbsent(key, content)
        }

        return imageKeys.firstNotNullOfOrNull { images[it] }
    }

    private fun attributesOf(tag: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        attribute.findAll(tag).forEach { match ->
            val name = match.groupValues[1].lowercase()
            val raw = match.groups[2]?.value ?: match.groups[3]?.value ?: match.groups[4]?.value ?: return@forEach
            result.putIfAbsent(name, decodeEntities(raw))
        }
        return result
    }

    /**
     * 属性値の実体参照を戻す。クエリ付きの URL は `&` が `&amp;` で書かれるので、
     * 戻さないと別の URL を取りに行くことになる。
     *
     * HTML の実体参照は 2000 を超えるが、属性値の URL に出るのはごく一部なので
     * 数値参照とその一部だけを見る。読めないものはそのまま残す
     */
    private fun decodeEntities(value: String): String {
        if ('&' !in value) return value

        val withNumeric = numericEntity.replace(value) { match ->
            val body = match.groupValues[1]
            val code =
                if (body.startsWith("x", ignoreCase = true)) {
                    body.drop(1).toIntOrNull(16)
                } else {
                    body.toIntOrNull()
                }
            if (code == null || code !in 1..Character.MAX_CODE_POINT) match.value else String(Character.toChars(code))
        }

        return namedEntities.entries.fold(withNumeric) { text, (name, replacement) ->
            text.replace("&$name;", replacement)
        }
    }
}
