package net.matsudamper.mastodon.rss.actor

import net.matsudamper.mastodon.rss.entity.PublicNoteId

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

    /**
     * Actor の `url`。人が開くほうのページで、[actorId] とは別のパスになる。
     *
     * Mastodon はリモートのアカウントを取り込むとき `id` を通信用の識別子、`url` を
     * 「元のページを開く」のリンク先として持つ。`url` を出さないか [actorId] を入れると、
     * 相手からは JSON を返すパスがプロフィールのリンクとして表示される。
     */
    val profileUrl: String = "https://$domain/$WEB_PROFILE_PREFIX$username"

    /**
     * 投稿の `url`。パーマリンクとして人が開くページで、投稿の `id` とは別のパス。
     *
     * 投稿の `id` にはアカウントの名前を入れない（[net.matsudamper.mastodon.rss.note.NoteUrls]）が、
     * こちらは画面のパスに合わせるので名前が入る。
     */
    fun notePageUrl(publicId: PublicNoteId): String = "$profileUrl/${publicId.value}"

    val inbox: String = "$actorId/inbox"
    val outbox: String = "$actorId/outbox"
    val featured: String = "$actorId/collections/featured"
    val followers: String = "$actorId/followers"
    val following: String = "$actorId/following"

    /**
     * プロフィール画像。フィードのアイコンをこちらで中継して返す。
     *
     * 配信元の URL をそのまま渡さないのは、フィードを差し替えてもアイコンの URL が
     * 変わらないようにするため。相手はアイコンを URL で覚える
     */
    val icon: String = "$actorId$ICON_PATH"

    /**
     * 取得元から決まる値を付けたプロフィール画像の URL。
     *
     * 相手はアイコンを URL で覚えるので、パスだけだと差し替えても前の画像が出続ける。
     * 値が変われば別の URL になり、取り直してもらえる
     */
    fun icon(sourceUrl: String): String = "$icon?v=${iconVersion(sourceUrl)}"

    /** Actor JSON の `publicKey.id`。署名の `keyId` としても飛んでくる */
    val publicKeyId: String = "$actorId#main-key"

    companion object {
        /**
         * 画面のアカウントのパスの先頭。`:frontend` の `Screen` と `staticRoutes` も同じ綴りを使う。
         *
         * ユーザー名に `@` は使えないので、この 1 文字で画面のパスだと判別できる
         */
        private const val WEB_PROFILE_PREFIX: String = "@"

        /**
         * アクターの id から見たプロフィール画像のパス。GraphQL も同じ綴りを使う
         */
        const val ICON_PATH: String = "/icon"

        /**
         * 取得元の URL から決まる短い値。
         *
         * 中身ではなく取得元で見るので、同じ URL のまま画像だけ差し替えられた場合は
         * 変わらない。GraphQL も同じ値を使う
         */
        fun iconVersion(sourceUrl: String): String = sourceUrl.hashCode().toUInt().toString(HEX_RADIX)

        private const val HEX_RADIX = 16
    }
}
