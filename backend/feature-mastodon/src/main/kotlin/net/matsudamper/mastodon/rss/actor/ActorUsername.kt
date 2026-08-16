package net.matsudamper.mastodon.rss.actor

/**
 * アクターのユーザー名の決まり。
 *
 * 設定から来る固定アクターの名前も、管理画面から追加される名前も、リクエストのパスや
 * WebFinger の `acct:` から来る名前も、同じ規則で検証する。URL のパスと `acct:` の
 * 両方に入るので、区切り文字が混ざると別のものを指してしまう。
 */
object ActorUsername {
    /**
     * 使える文字は英数字と `_` `.` `-`。先頭と末尾は英数字か `_`。
     * Mastodon が受け付ける範囲に合わせて狭く取っている。
     */
    private val PATTERN = Regex("^[A-Za-z0-9_]([A-Za-z0-9_.-]*[A-Za-z0-9_])?$")

    /** Mastodon のローカルアカウントの上限に合わせる。長い名前は相手側で扱えない */
    const val MAX_LENGTH: Int = 30

    fun isValid(username: String): Boolean = username.length <= MAX_LENGTH && PATTERN.matches(username)
}
