package net.matsudamper.mastodon.rss.feed

/**
 * フィードから来た HTML を、そのまま配信してよい形に削る。
 *
 * 記事本文は配信元が書いた HTML がそのまま入ってくる。これを検証せずに `Note` の
 * `content` に入れると、受け取った Mastodon が表示する時点で問題になる（Mastodon 自身も
 * 受信時にサニタイズするが、こちらが送るものの中身をあちらの実装に任せる理由は無い）。
 * 出力に残すタグを許可リストで決め、それ以外は落とす。
 *
 * 方針:
 *
 * - 許可したタグだけを残す。知らないタグは落とすが、中身のテキストは残す
 *   （`<div>` や `<figure>` で囲まれているだけの本文が丸ごと消えないように）
 * - `<script>` と `<style>` は中身ごと落とす。ここのテキストは本文ではない
 * - 属性は許可リスト。いまは `<a href>` だけ。`style` や `on*` を通さないため、
 *   個別に弾くのではなく「許可したもの以外は全部落とす」形にする
 * - `href` のスキームも許可リスト。`javascript:` のようなものを残さない
 * - 閉じられていないタグは末尾で閉じる。対応しない閉じタグは落とす。
 *   壊れた入れ子をそのまま流すと、受信側の表示が本文の外まで崩れる
 *
 * HTML パーサとしては簡易なもので、ブラウザの挙動を再現するものではない。
 * 目的は「安全な部分集合を取り出す」ことなので、判断に迷うものは落とす方に倒す。
 */
object HtmlSanitizer {
    /**
     * 既定で残すタグ。
     *
     * Mastodon が表示に使うのはこの程度で、それ以上を送っても相手側で落ちる。
     * 見出しやリストを残しても、あちらでは `<p>` に潰れるか消える。
     */
    val DEFAULT_ALLOWED_TAGS: Set<String> = setOf("p", "br", "a", "span")

    /** 中身を持たないタグ。閉じタグを待たない */
    private val VOID_TAGS: Set<String> = setOf("br", "hr", "img", "wbr")

    /** 中身のテキストごと落とすタグ */
    private val DROP_CONTENT_TAGS: Set<String> = setOf("script", "style")

    /** タグごとに残す属性 */
    private val ALLOWED_ATTRIBUTES: Map<String, Set<String>> = mapOf("a" to setOf("href"))

    /** `href` に許すスキーム。相対 URL（`:` を含まないもの）も許す */
    private val ALLOWED_URL_SCHEMES: Set<String> = setOf("http", "https", "mailto")

    /**
     * 改行として扱うタグ。プレーンテキスト化のときだけ効く。
     *
     * 段落の区切りを空白 1 個にすると、複数段落の本文が 1 行に潰れて読めなくなる。
     */
    private val LINE_BREAK_TAGS: Set<String> = setOf("br", "p", "div", "li", "tr", "h1", "h2", "h3", "h4", "h5", "h6")

    /**
     * 許可したタグだけを残した HTML を返す。
     *
     * 入力のテキスト部分は実体参照を解かずにそのまま通す（`&amp;` を `&` に戻すと
     * 出力としては壊れる）。ただし実体参照になっていない裸の `&` と `<` `>` は
     * エスケープする。壊れた HTML を渡されても出力は壊さない。
     */
    fun sanitize(
        html: String,
        allowedTags: Set<String> = DEFAULT_ALLOWED_TAGS,
    ): String {
        val builder = StringBuilder()
        val openTags = ArrayDeque<String>()

        scan(
            html = html,
            onText = { text -> builder.append(escapeTextKeepingEntities(text)) },
            onStartTag = { tag ->
                val name = tag.name
                if (name in allowedTags) {
                    builder.append('<').append(name)
                    appendAttributes(builder = builder, tag = tag)
                    builder.append('>')
                    if (name !in VOID_TAGS && !tag.selfClosing) {
                        openTags.addLast(name)
                    }
                }
            },
            onEndTag = { name ->
                if (name in allowedTags && name !in VOID_TAGS && openTags.contains(name)) {
                    // 閉じ忘れているタグを先に閉じる。<a><p></a> のような入れ子を
                    // そのまま出すと、受信側で本文の外まで巻き込んで崩れる
                    while (openTags.isNotEmpty()) {
                        val open = openTags.removeLast()
                        builder.append("</").append(open).append('>')
                        if (open == name) break
                    }
                }
            },
        )

        while (openTags.isNotEmpty()) {
            builder.append("</").append(openTags.removeLast()).append('>')
        }

        return builder.toString()
    }

    /**
     * タグを落として実体参照を戻したプレーンテキストを返す。
     *
     * 記事の題名や、文字数を数えたいときに使う。段落や `<br>` は改行にする。
     */
    fun toPlainText(html: String): String {
        val builder = StringBuilder()

        scan(
            html = html,
            onText = { text -> builder.append(decodeEntities(text)) },
            onStartTag = { tag -> if (tag.name in LINE_BREAK_TAGS) builder.append('\n') },
            onEndTag = { name -> if (name in LINE_BREAK_TAGS) builder.append('\n') },
        )

        return FeedText.normalizeWhitespace(builder.toString())
    }

    /**
     * プレーンテキストを HTML に埋め込める形にする。
     *
     * こちらは実体参照を解釈しない。`&amp;` と書かれたテキストは、
     * そう書きたかったものとして `&amp;amp;` にする。
     */
    fun escapeText(text: String): String {
        val builder = StringBuilder(text.length)
        for (character in text) {
            when (character) {
                '&' -> builder.append("&amp;")
                '<' -> builder.append("&lt;")
                '>' -> builder.append("&gt;")
                '"' -> builder.append("&quot;")
                else -> builder.append(character)
            }
        }
        return builder.toString()
    }

    /**
     * 実体参照を文字に戻す。
     *
     * 知らない名前はそのまま残す。`&foo;` を消すと本文から文字が減るし、
     * `&` に戻すと別の実体参照に化けることがある。
     */
    fun decodeEntities(text: String): String {
        if (!text.contains('&')) return text

        val builder = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val character = text[index]
            if (character != '&') {
                builder.append(character)
                index++
                continue
            }

            val end = text.indexOf(';', index + 1)
            // 実体参照は短い。遠くの ; まで拾うと、本文中の裸の & が
            // 関係のない場所まで巻き込む
            if (end < 0 || end - index > MAX_ENTITY_LENGTH) {
                builder.append(character)
                index++
                continue
            }

            val body = text.substring(index + 1, end)
            val decoded = decodeEntityBody(body)
            if (decoded == null) {
                builder.append(character)
                index++
            } else {
                builder.append(decoded)
                index = end + 1
            }
        }
        return builder.toString()
    }

    /** `&` と `;` の間だけを受け取って文字に直す。読めなければ null */
    private fun decodeEntityBody(body: String): String? {
        if (body.isEmpty()) return null

        if (body[0] == '#') {
            val isHex = body.length > 1 && (body[1] == 'x' || body[1] == 'X')
            val digits = if (isHex) body.substring(2) else body.substring(1)
            if (digits.isEmpty()) return null
            val codePoint = digits.toIntOrNull(radix = if (isHex) 16 else 10) ?: return null
            // サロゲートや範囲外は文字にできない
            if (codePoint <= 0 || codePoint > Character.MAX_CODE_POINT) return null
            if (codePoint in 0xD800..0xDFFF) return null
            return String(Character.toChars(codePoint))
        }

        return NAMED_ENTITIES[body]
    }

    /**
     * テキスト部分を出力用にエスケープする。実体参照になっているものは触らない。
     */
    private fun escapeTextKeepingEntities(text: String): String {
        val builder = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            when (val character = text[index]) {
                '<' -> {
                    builder.append("&lt;")
                }

                '>' -> {
                    builder.append("&gt;")
                }

                '&' -> {
                    val end = text.indexOf(';', index + 1)
                    val body = if (end in 0..(index + MAX_ENTITY_LENGTH)) text.substring(index + 1, end) else null
                    if (body != null && decodeEntityBody(body) != null) {
                        builder.append('&').append(body).append(';')
                        index = end
                    } else {
                        builder.append("&amp;")
                    }
                }

                else -> {
                    builder.append(character)
                }
            }
            index++
        }
        return builder.toString()
    }

    private fun appendAttributes(
        builder: StringBuilder,
        tag: StartTag,
    ) {
        val allowed = ALLOWED_ATTRIBUTES[tag.name] ?: return
        for ((name, value) in tag.attributes) {
            if (name !in allowed) continue
            val cleaned = if (name == "href") sanitizeUrl(value) else value
            if (cleaned == null) continue
            builder
                .append(' ')
                .append(name)
                .append("=\"")
                .append(escapeText(cleaned))
                .append('"')
        }
    }

    /**
     * URL のスキームを確かめる。許可していないものは null。
     *
     * スキームの判定の前に実体参照を戻す。`&#106;avascript:` のような書き方で
     * すり抜けられるため。
     */
    private fun sanitizeUrl(raw: String): String? {
        val value = decodeEntities(raw).trim()
        if (value.isEmpty()) return null
        // 制御文字を挟んでスキームを隠す書き方があるので、含むものは通さない
        if (value.any { it.code < 0x20 || it.code == 0x7F }) return null

        val colon = value.indexOf(':')
        val slash = value.indexOf('/')
        // ':' より前に '/' があるならスキームではなくパス。相対 URL として通す
        if (colon < 0 || (slash in 0..<colon)) return value

        val scheme = value.substring(0, colon).lowercase()
        return if (scheme in ALLOWED_URL_SCHEMES) value else null
    }

    /**
     * HTML を前から見て、テキストとタグに切り分ける。
     *
     * `sanitize` と `toPlainText` で共通の処理はここまでで、違うのは
     * 切り分けたものをどう組み立てるかだけ。
     */
    private inline fun scan(
        html: String,
        onText: (String) -> Unit,
        onStartTag: (StartTag) -> Unit,
        onEndTag: (String) -> Unit,
    ) {
        var index = 0
        while (index < html.length) {
            val open = html.indexOf('<', index)
            if (open < 0) {
                onText(html.substring(index))
                return
            }
            if (open > index) onText(html.substring(index, open))

            // コメント・DOCTYPE・処理命令。中身は本文ではないので落とす
            if (html.startsWith("<!--", open)) {
                val end = html.indexOf("-->", open + 4)
                index = if (end < 0) html.length else end + 3
                continue
            }
            if (html.startsWith("<!", open) || html.startsWith("<?", open)) {
                val end = html.indexOf('>', open)
                index = if (end < 0) html.length else end + 1
                continue
            }

            if (html.startsWith("</", open)) {
                val end = html.indexOf('>', open)
                if (end < 0) {
                    // 閉じられていない。ここから先はタグとして読めないので捨てる
                    return
                }
                val name = html.substring(open + 2, end).trim().lowercase()
                if (name.isNotEmpty()) onEndTag(name)
                index = end + 1
                continue
            }

            // '<' の次が名前でなければタグではない。比較演算子として書かれた '<'
            if (open + 1 >= html.length || !html[open + 1].isLetter()) {
                onText("<")
                index = open + 1
                continue
            }

            val tag = parseStartTag(html = html, start = open)
            if (tag == null) {
                onText("<")
                index = open + 1
                continue
            }

            if (tag.name in DROP_CONTENT_TAGS) {
                index = skipElement(html = html, name = tag.name, from = tag.end)
                continue
            }

            onStartTag(tag)
            index = tag.end
        }
    }

    /** `<name attr="value">` を読む。読み切れなければ null */
    private fun parseStartTag(
        html: String,
        start: Int,
    ): StartTag? {
        var index = start + 1
        val nameStart = index
        while (index < html.length && (html[index].isLetterOrDigit() || html[index] == '-' || html[index] == ':')) {
            index++
        }
        val name = html.substring(nameStart, index).lowercase()
        if (name.isEmpty()) return null

        val attributes = mutableListOf<Pair<String, String>>()
        var selfClosing = false

        while (index < html.length) {
            while (index < html.length && html[index].isWhitespace()) index++
            if (index >= html.length) return null

            if (html[index] == '>') {
                index++
                break
            }
            if (html[index] == '/') {
                selfClosing = true
                index++
                continue
            }

            val attributeNameStart = index
            while (index < html.length && !html[index].isWhitespace() &&
                html[index] != '=' && html[index] != '>' && html[index] != '/'
            ) {
                index++
            }
            val attributeName = html.substring(attributeNameStart, index).lowercase()
            if (index >= html.length) return null

            while (index < html.length && html[index].isWhitespace()) index++
            if (index < html.length && html[index] == '=') {
                index++
                while (index < html.length && html[index].isWhitespace()) index++
                if (index >= html.length) return null

                val quote = html[index]
                val value: String
                if (quote == '"' || quote == '\'') {
                    index++
                    val valueStart = index
                    while (index < html.length && html[index] != quote) index++
                    if (index >= html.length) return null
                    value = html.substring(valueStart, index)
                    index++
                } else {
                    val valueStart = index
                    while (index < html.length && !html[index].isWhitespace() && html[index] != '>') index++
                    value = html.substring(valueStart, index)
                }
                if (attributeName.isNotEmpty()) attributes.add(attributeName to value)
            } else if (attributeName.isNotEmpty()) {
                attributes.add(attributeName to "")
            }
        }

        return StartTag(
            name = name,
            attributes = attributes,
            selfClosing = selfClosing,
            end = index,
        )
    }

    /** `<script>` のように中身ごと落とすタグを、閉じタグの後ろまで読み飛ばす */
    private fun skipElement(
        html: String,
        name: String,
        from: Int,
    ): Int {
        var index = from
        while (index < html.length) {
            val open = html.indexOf("</", index)
            if (open < 0) return html.length
            val end = html.indexOf('>', open)
            if (end < 0) return html.length
            if (html.substring(open + 2, end).trim().equals(name, ignoreCase = true)) return end + 1
            index = end + 1
        }
        return html.length
    }

    private class StartTag(
        val name: String,
        val attributes: List<Pair<String, String>>,
        val selfClosing: Boolean,
        /** タグの `>` の次の位置 */
        val end: Int,
    )

    /**
     * `&` と `;` の間に許す長さ。名前付き実体参照で最も長いものでも 30 文字ほど。
     */
    private const val MAX_ENTITY_LENGTH = 32

    /**
     * 名前付き実体参照。
     *
     * HTML5 の一覧は 2000 件を超えるが、フィードの本文に実際に現れるのは
     * この程度。足りなければ足す。知らない名前はそのまま残すので、
     * 網羅していなくても文字が消えることはない。
     */
    private val NAMED_ENTITIES: Map<String, String> =
        mapOf(
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'",
            "nbsp" to " ",
            "copy" to "©",
            "reg" to "®",
            "trade" to "™",
            "deg" to "°",
            "plusmn" to "±",
            "times" to "×",
            "divide" to "÷",
            "middot" to "·",
            "bull" to "•",
            "hellip" to "…",
            "ndash" to "–",
            "mdash" to "—",
            "lsquo" to "‘",
            "rsquo" to "’",
            "ldquo" to "“",
            "rdquo" to "”",
            "laquo" to "«",
            "raquo" to "»",
            "yen" to "¥",
            "euro" to "€",
            "pound" to "£",
            "cent" to "¢",
            "sect" to "§",
            "para" to "¶",
            "dagger" to "†",
            "permil" to "‰",
            "larr" to "←",
            "rarr" to "→",
            "harr" to "↔",
        )
}
