package net.matsudamper.mastodon.rss.note

/**
 * 投稿の URL。
 *
 * 相手は受け取った id をそのまま覚えるので、後から変えられない。
 * アカウントの名前をパスに入れないのは、名前を変えても URL を保つため。
 */
data class NoteUrls(
    val domain: String,
    val publicId: String,
) {
    val noteId: String = "https://$domain/notes/$publicId"

    /**
     * `Create` 自身の id。GET できる文書は無いのでフラグメントを付ける
     */
    val createId: String = "$noteId#create"
}
