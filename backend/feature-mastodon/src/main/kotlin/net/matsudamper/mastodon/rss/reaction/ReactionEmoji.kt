package net.matsudamper.mastodon.rss.reaction

/**
 * 反応に載ってくる絵文字の読み方。
 *
 * Mastodon の「お気に入り」は `content` を持たない `Like` で届く。Misskey は同じ
 * `Like` の `content` に押した絵文字を載せ、カスタム絵文字は `:name:` か
 * `:name@host:`（相手のサーバーから見た呼び名）の形になる。`EmojiReact` の
 * `content` も同じ形。
 */
internal object ReactionEmoji {
    /**
     * お気に入り。絵文字を伴わない反応
     */
    const val FAVOURITE: String = ""

    /**
     * 受け付ける長さの上限。
     *
     * 仕様上の上限は無く、`content` には本文と同じだけの文字列を入れられる。
     * 押した絵文字 1 つとして扱えない長さのものは記録しない
     */
    private const val MAX_LENGTH = 100

    /**
     * 記録する形にする。絵文字として受け付けられなければ null
     */
    fun of(content: String?): String? {
        val trimmed = content.orEmpty().trim()
        if (trimmed.length > MAX_LENGTH) return null
        return trimmed
    }
}
