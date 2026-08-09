package net.matsudamper.mastodon.rss.feed

/**
 * フィードから取り出した文字列を整えるための小物。
 *
 * XML の要素の中身はインデントの空白や改行をそのまま含むので、取り出した値を
 * そのまま題名や本文にすると余計な空白が入る。文字数の調整も、投稿の文字数制限が
 * あるインスタンスに向けて必要になる。
 */
object FeedText {
    /**
     * 空白を整える。
     *
     * - 行内の連続した空白は 1 個にする
     * - 各行の前後の空白を落とす
     * - 空行が 2 行以上続いたら 1 行にまとめる
     * - 全体の前後の空白を落とす
     *
     * 改行を残すのは、段落のある本文を 1 行に潰さないため。
     */
    fun normalizeWhitespace(text: String): String {
        val lines =
            text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .split('\n')
                .map { line -> collapseSpaces(line) }

        val builder = StringBuilder()
        var blankLines = 0
        for (line in lines) {
            if (line.isEmpty()) {
                blankLines++
                continue
            }
            if (builder.isNotEmpty()) {
                builder.append('\n')
                // 空行は 1 行だけ残す。配信元の HTML には空行だけが延々と続くものがある
                if (blankLines > 0) builder.append('\n')
            }
            blankLines = 0
            builder.append(line)
        }
        return builder.toString()
    }

    /** 1 行ぶんの空白を整える。連続した空白は 1 個にして、前後の空白は落とす */
    private fun collapseSpaces(line: String): String {
        val builder = StringBuilder(line.length)
        var pendingSpace = false
        for (character in line) {
            if (character.isWhitespace()) {
                pendingSpace = true
                continue
            }
            if (pendingSpace && builder.isNotEmpty()) builder.append(' ')
            pendingSpace = false
            builder.append(character)
        }
        return builder.toString()
    }

    /**
     * 1 行にする。題名のように改行を持てない場所で使う。
     */
    fun singleLine(text: String): String = collapseSpaces(normalizeWhitespace(text).replace('\n', ' '))

    /**
     * [maxLength] 文字に収める。切ったときは末尾に [ellipsis] を付ける。
     *
     * 数えるのはコードポイント。`String.length` で切ると、絵文字のような
     * サロゲートペアの途中で切れて壊れた文字になる。書記素クラスタ（結合文字や
     * 異体字セレクタ）までは見ていないので、そこで切れると見た目が変わることはある。
     *
     * 区切りの良い場所で切れるよう、切る位置の少し手前に空白があればそこまで戻す。
     * 日本語のように空白が無い文章では戻さずそのまま切る。
     */
    fun truncate(
        text: String,
        maxLength: Int,
        ellipsis: String = "…",
    ): String {
        require(maxLength > 0) { "maxLength は 1 以上である必要がある: $maxLength" }

        val length = text.codePointCount(0, text.length)
        if (length <= maxLength) return text

        val ellipsisLength = ellipsis.codePointCount(0, ellipsis.length)
        // 省略記号だけで上限を超える場合は、記号を諦めて本文を入れる
        if (ellipsisLength >= maxLength) return text.substring(0, text.offsetByCodePoints(0, maxLength))

        val cut = text.offsetByCodePoints(0, maxLength - ellipsisLength)
        val head = text.substring(0, cut)

        // 切った位置が単語の途中でなければ戻す必要は無い
        val splitsWord = cut < text.length && !text[cut].isWhitespace()
        val lastSpace = head.indexOfLast { it.isWhitespace() }
        val trimmed =
            if (splitsWord && lastSpace >= 0 && head.length - lastSpace <= WORD_BOUNDARY_LOOK_BACK) {
                head.substring(0, lastSpace)
            } else {
                head
            }

        return trimmed.trimEnd() + ellipsis
    }

    /**
     * 切る位置から何文字ぶん戻って空白を探すか。
     *
     * 長く戻ると、空白が少ない文章で本文が大きく削れる。単語 1 個ぶんに留める。
     */
    private const val WORD_BOUNDARY_LOOK_BACK = 16
}
