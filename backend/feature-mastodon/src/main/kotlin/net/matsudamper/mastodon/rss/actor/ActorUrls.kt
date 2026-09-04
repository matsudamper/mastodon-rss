package net.matsudamper.mastodon.rss.actor

import net.matsudamper.mastodon.rss.entity.ActivityPubId

/**
 * アクターの識別子と URL。
 *
 * WebFinger の `subject`、Actor の `id`、`publicKey.id`、inbox の宛先はすべて
 * ドメインとユーザー名から機械的に決まる。組み立てを散らすと 1 箇所だけ
 * 綴りが違う、という形の不具合になり、相手側のキャッシュに残って厄介なので
 * ここに集約する。
 *
 * scheme は常に `https`。ActivityPub の実装は平文 HTTP のアクターを
 * 受け付けないことが多く、開発時もトンネル越しに HTTPS で公開するため。
 */
data class ActorUrls(
    val domain: String,
    val username: String,
) {
    /** WebFinger の `subject`。`acct:admin@example.com` の形 */
    val acct: String = "acct:$username@$domain"

    /**
     * Mastodon の検索窓に貼る形。`acct:` を付けたままだと引けない
     */
    val mention: String = "@$username@$domain"

    /** Actor の `id`。Mastodon 側にキャッシュされる本体 */
    val actorId: String = "https://$domain/users/$username"

    val inbox: String = "$actorId/inbox"
    val outbox: String = "$actorId/outbox"
    val featured: String = "$actorId/collections/featured"
    val followers: String = "$actorId/followers"
    val following: String = "$actorId/following"

    /**
     * このアクターを消したことを伝える `Delete` 自身の id。
     *
     * アクターの id と同じにすると、相手の重複判定でアクター文書と衝突する
     */
    val deleteId: ActivityPubId = ActivityPubId("$actorId#delete")

    /** Actor JSON の `publicKey.id`。署名の `keyId` としても飛んでくる */
    val publicKeyId: String = "$actorId#main-key"
}
