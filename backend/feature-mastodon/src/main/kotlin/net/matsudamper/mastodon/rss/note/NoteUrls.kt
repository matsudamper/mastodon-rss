package net.matsudamper.mastodon.rss.note

/**
 * 投稿の URL。
 *
 * アクターの URL と同じく、綴りを散らすと 1 か所だけ違う形になり、
 * 相手のキャッシュに残って後から直せない。組み立てはここに集約する。
 *
 * アカウントの名前をパスに入れないのは、名前が変わっても投稿の URL が
 * 変わらないようにするため。相手は受け取った id をそのまま覚えている。
 */
data class NoteUrls(
    val domain: String,
    val publicId: String,
) {
    val noteId: String = "https://$domain/notes/$publicId"

    /**
     * `Create` 自身の id。GET できる文書があると読める形にしないためフラグメントを付ける
     */
    val createId: String = "$noteId#create"

    /**
     * `Delete` 自身の id。[createId] と同じ理由でフラグメントを付ける
     */
    val deleteId: String = "$noteId#delete"
}
