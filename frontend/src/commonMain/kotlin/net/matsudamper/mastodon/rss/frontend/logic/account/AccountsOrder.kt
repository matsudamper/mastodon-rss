package net.matsudamper.mastodon.rss.frontend.logic.account

enum class AccountsOrder {
    /**
     * 追加した順。新しいものが先
     */
    AddedNewest,

    /**
     * 最後に投稿した順。新しいものが先
     */
    LatestNote,
}
