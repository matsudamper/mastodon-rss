package net.matsudamper.mastodon.rss.note

import net.matsudamper.mastodon.rss.activitypub.ActivityPubId

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
    /**
     * 投稿の URL。これがそのまま投稿の id になる
     */
    val noteUrl: String = "https://$domain/notes/$publicId"

    val noteId: ActivityPubId = ActivityPubId(noteUrl)

    /**
     * `Create` 自身の id。GET できる文書があると読める形にしないためフラグメントを付ける
     */
    val createId: ActivityPubId = ActivityPubId("$noteUrl#create")

    /**
     * `Delete` 自身の id。[createId] と同じ理由でフラグメントを付ける
     */
    val deleteId: ActivityPubId = ActivityPubId("$noteUrl#delete")
}
