package net.matsudamper.mastodon.rss.logic

/**
 * 管理画面に書かれたテキストを、配信できる本文に直す。
 *
 * HTML をそのまま受けると、管理画面を通して任意のタグをフォロワーに配ることになる。
 * 投函に渡してよいのはここを通した本文だけ。
 */
object NoteComposer {
    /**
     * 本文の長さの上限。
     *
     * Mastodon の既定の投稿長は 500 文字で、超えた分は相手側で切られる。
     * ここで弾いておけば、切られたものが配られてから気付く形にならない。
     */
    const val MAX_LENGTH: Int = 500

    fun compose(body: String): ComposeResult {
        val text = body.trim()
        if (text.isEmpty()) return ComposeResult.Empty
        // 書いた人にとっての文字数と合わせるため、コードポイントで数える
        if (text.codePointCount(0, text.length) > MAX_LENGTH) return ComposeResult.TooLong

        return ComposeResult.Composed(toHtml(text))
    }

    /**
     * プレーンテキストを配信する HTML に直す。
     *
     * 空行で段落に分け、行の切れ目は `<br>` にする。Mastodon が許可するのは
     * この程度のタグで、それ以外は相手側で落とされる。
     */
    private fun toHtml(text: String): String = text
        .replace("\r\n", "\n")
        .split(Regex("\n{2,}"))
        .filter { it.isNotBlank() }
        .joinToString("") { paragraph ->
            val escaped = paragraph.trim().split("\n").joinToString("<br>") { escapeHtml(it) }
            "<p>$escaped</p>"
        }

    private fun escapeHtml(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    sealed interface ComposeResult {
        data class Composed(
            val contentHtml: String,
        ) : ComposeResult

        data object Empty : ComposeResult

        data object TooLong : ComposeResult
    }
}
