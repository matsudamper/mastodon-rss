package dev.matsudamper.mastodonrss.repository.sqlite

/**
 * マイグレーションの SQL を文単位に分割する。
 *
 * JDBC の `Statement.execute` は 1 文しか受け取れないので、`;` で区切る。
 * ただし単純な文字列分割だと、文字列リテラルやコメントの中の `;` で誤って切れる。
 * 引用符とコメントを読み飛ばしながら区切る。
 *
 * トリガーの `BEGIN ... END;` のように本体に `;` を含む構文には対応していない。
 * 必要になったらファイルを分けるか、ここを拡張すること。
 */
internal fun splitSqlStatements(sql: String): List<String> {
    val statements = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0

    fun flush() {
        val statement = current.toString().trim()
        if (statement.isNotEmpty()) {
            statements.add(statement)
        }
        current.clear()
    }

    while (index < sql.length) {
        val char = sql[index]
        when {
            sql.startsWith("--", index) -> {
                // 行コメント。改行までを読み飛ばす
                val lineEnd = sql.indexOf('\n', index)
                index = if (lineEnd == -1) sql.length else lineEnd + 1
                current.append('\n')
            }

            sql.startsWith("/*", index) -> {
                val commentEnd = sql.indexOf("*/", index + 2)
                require(commentEnd != -1) { "ブロックコメントが閉じられていない" }
                index = commentEnd + 2
                current.append(' ')
            }

            // ' は文字列リテラル、" は識別子の引用。どちらも中身を解釈しない
            char == '\'' || char == '"' -> {
                val quoteEnd = findQuoteEnd(sql, index, char)
                current.append(sql, index, quoteEnd)
                index = quoteEnd
            }

            char == ';' -> {
                flush()
                index++
            }

            else -> {
                current.append(char)
                index++
            }
        }
    }

    // 最後の文は `;` で終わっていないことがある
    flush()

    return statements
}

/**
 * [start] にある引用符に対応する閉じ引用符の次の位置を返す。
 * SQL では引用符を 2 つ重ねるとエスケープになる（`'it''s'`）。
 */
private fun findQuoteEnd(
    sql: String,
    start: Int,
    quote: Char,
): Int {
    var index = start + 1
    while (index < sql.length) {
        if (sql[index] != quote) {
            index++
            continue
        }
        if (index + 1 < sql.length && sql[index + 1] == quote) {
            index += 2
        } else {
            return index + 1
        }
    }
    throw IllegalArgumentException("引用符 $quote が閉じられていない")
}
