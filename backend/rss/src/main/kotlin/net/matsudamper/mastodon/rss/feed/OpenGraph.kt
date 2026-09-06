package net.matsudamper.mastodon.rss.feed

/**
 * 記事のリンク先の HTML から Open Graph protocol の値を読む。
 *
 * ネットワークは触らない。[YouTubeFeedResolver] と同じく、取得は HTTP クライアントを
 * 持つ `:backend` 側に任せて、ここは文字列を見るだけにする。
 *
 * 木は組み立てず、`<meta>` を拾いながら先頭から 1 回だけなぞる。相手のページは
 * 壊れた入れ子や閉じ忘れを含むことがあり、木にできないと何も読めないのでは
 * 取りこぼしが多すぎる。
 *
 * なぞる向きを戻さないのは、リンク先が配信元の書いたページだから。閉じていない
 * コメントやタグを並べれば、位置ごとに終端を探し直す作りは入力長の二乗になり、
 * ページ 1 本で取り込みが止まる。
 *
 * `<script>` とコメントの中身はタグに見えてもタグではないので、範囲ごと飛ばす。
 * 飛ばさないと、中に書かれた `</head>` で打ち切ったり、中の `<meta>` を拾ったりする。
 */
object OpenGraph {
    /**
     * 画像を探す順。前にあるものを優先する。
     *
     * `secure_url` を先に見るのは https 版だから。`og:image` が http のページでは、
     * 添付を取りに来た相手が拒むことがある。
     */
    private val imageKeys = listOf("og:image:secure_url", "og:image", "og:image:url")

    /** 中身を文字列として扱う要素。閉じるまで飛ばす */
    private val rawTextTags = setOf("script", "style", "template", "noscript")

    /** そこから先は `<head>` の外。探すのをやめる */
    private val headEndTags = setOf("/head", "body")

    /** `name="value"` / `name='value'` / 引用符なしの `name=value` */
    private val attribute = Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""")

    /** 数値参照と、URL に出うる名前付きの参照 */
    private val entity = Regex("&(#[xX][0-9A-Fa-f]+|#[0-9]+|[A-Za-z][A-Za-z0-9]*);")

    private val namedEntities = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        // 半角スペースではない
        "nbsp" to "\u00A0",
    )

    /**
     * 画像の URL を返す。相対 URL のまま返すので、絶対化と http / https の確認は
     * 呼び出し側で [HttpUrl.sanitize] に通すこと。
     *
     * 複数枚あるページでは最初の 1 枚を採る。OGP は先頭を代表の画像とする決まり。
     */
    fun imageUrl(html: String): String? {
        val images = mutableMapOf<String, String>()
        var index = 0

        while (index < html.length) {
            val tagStart = html.indexOf('<', index)
            if (tagStart < 0) break

            if (html.startsWith(COMMENT_OPEN, tagStart)) {
                val commentEnd = html.indexOf(COMMENT_CLOSE, tagStart + COMMENT_OPEN.length)
                // 閉じていないコメント。ここから先は全部コメントの中
                if (commentEnd < 0) break
                index = commentEnd + COMMENT_CLOSE.length
                continue
            }

            val name = tagNameAt(html, tagStart)
            if (name == null) {
                index = tagStart + 1
                continue
            }
            if (name in headEndTags) break

            val tagEnd = tagEndAt(html, tagStart)
            // 閉じていないタグ。属性が揃っていないので読まない
            if (tagEnd < 0) break

            if (name == "meta") {
                imageOf(html.substring(tagStart, tagEnd + 1))?.let { (key, content) ->
                    images.putIfAbsent(key, content)
                }
            }

            index = if (name in rawTextTags) {
                val closing = html.indexOf("</$name", tagEnd + 1, ignoreCase = true)
                // 閉じていない script などの中に本物の meta は無い
                if (closing < 0) break else closing
            } else {
                tagEnd + 1
            }
        }

        return imageKeys.firstNotNullOfOrNull { key -> images[key] }
    }

    /**
     * `<` の次から始まるタグの名前。閉じるタグは `/` 付きで返す。
     * タグに見えない場合は null
     */
    private fun tagNameAt(
        html: String,
        tagStart: Int,
    ): String? {
        var index = tagStart + 1
        val name = StringBuilder()
        if (index < html.length && html[index] == '/') {
            name.append('/')
            index++
        }
        while (index < html.length && html[index].isLetterOrDigit()) {
            name.append(html[index].lowercaseChar())
            index++
        }

        val bare = name.removePrefix("/")
        if (bare.isEmpty()) return null
        // 名前の後ろが属性の区切りでなければ、タグではなく本文の `<`
        val next = html.getOrNull(index) ?: return null
        if (!next.isWhitespace() && next != '>' && next != '/') return null

        return name.toString()
    }

    /**
     * タグを閉じる `>` の位置。引用符の中の `>` は数えない。
     * 閉じていなければ -1
     */
    private fun tagEndAt(
        html: String,
        tagStart: Int,
    ): Int {
        var quote: Char? = null
        var index = tagStart + 1
        while (index < html.length) {
            val character = html[index]
            when {
                quote != null -> if (character == quote) quote = null
                character == '"' || character == '\'' -> quote = character
                character == '>' -> return index
            }
            index++
        }
        return -1
    }

    /**
     * `<meta ...>` 1 つから、画像として使える値を取り出す。
     * 画像を指していなければ null
     */
    private fun imageOf(tag: String): Pair<String, String>? {
        val attributes = attributesOf(tag)
        // 相手のページは property と name のどちらでも書いてくる。
        // OGP の決まりは property だが、name で書くページも実際にある
        val key = (attributes["property"] ?: attributes["name"])?.lowercase() ?: return null
        if (key !in imageKeys) return null
        val content = attributes["content"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return key to content
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
     * 戻すのは 1 回だけ。繰り返すと `&#38;lt;` のような書き方が `<` に化ける。
     * HTML の実体参照は 2000 を超えるが、属性値の URL に出るのはごく一部なので
     * 数値参照とその一部だけを見る。読めないものはそのまま残す
     */
    private fun decodeEntities(value: String): String {
        if ('&' !in value) return value

        return entity.replace(value) { match ->
            val body = match.groupValues[1]
            if (!body.startsWith('#')) {
                return@replace namedEntities[body.lowercase()] ?: match.value
            }

            val digits = body.drop(1)
            val code = if (digits.startsWith("x", ignoreCase = true)) {
                digits.drop(1).toIntOrNull(16)
            } else {
                digits.toIntOrNull()
            }
            if (code == null || code !in 1..Character.MAX_CODE_POINT) match.value else String(Character.toChars(code))
        }
    }

    private const val COMMENT_OPEN = "<!--"
    private const val COMMENT_CLOSE = "-->"
}
