package net.matsudamper.mastodon.rss.shared

/**
 * 画面のパス。
 *
 * 画面を出す frontend と、そのパスを外向きの URL として申告する backend の
 * 両方が同じ綴りを使う。片方だけ変えると、相手からのリンクだけが 404 になったり、
 * 画面はあるのに誰も辿り着けなくなったりする。
 *
 * ここに置くのは外から指されるパスだけ。画面の中だけで完結するパスは
 * frontend の `Screen` が持つ。
 */
object WebPagePath {
    /**
     * アカウントの画面のパスの先頭。
     *
     * ユーザー名に `@` は使えないので、この 1 文字で画面のパスだと判別できる
     */
    const val ACCOUNT_PREFIX: String = "@"

    /** アカウントの画面 */
    fun account(username: String): String = "/$ACCOUNT_PREFIX$username"

    /** 投稿 1 件の画面 */
    fun accountNote(
        username: String,
        noteId: String,
    ): String = "${account(username)}/$noteId"
}
