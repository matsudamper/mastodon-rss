package net.matsudamper.mastodon.rss.actor

/**
 * アクターのユーザー名の決まり。
 *
 * 設定から来る固定アクターの名前も、リクエストのパスや WebFinger の `acct:` から
 * 来る名前も、同じ規則で検証する。URL のパスと `acct:` の両方に入るので、
 * 区切り文字が混ざると別のものを指してしまう。
 */
object ActorUsername {
    /**
     * 動作確認用アクターの接頭辞。
     *
     * Mastodon はリモートアクターを永続キャッシュするので、内容を間違えたまま
     * 一度取得されると相手側からは直せない。検証のたびに名前を変えられるよう、
     * この接頭辞で始まる名前は使い捨てのアクターとして扱う。
     *
     * Phase 6 でアクターを DB から作れるようになったら消す。検証用のアカウントも
     * 普通に作って消せるようになるため。TODO.md の Phase 6 に項目がある。
     */
    const val TEST_PREFIX: String = "test-"

    /**
     * 使える文字は英数字と `_` `.` `-`。先頭と末尾は英数字か `_`。
     * Mastodon が受け付ける範囲に合わせて狭く取っている。
     */
    private val PATTERN = Regex("^[A-Za-z0-9_]([A-Za-z0-9_.-]*[A-Za-z0-9_])?$")

    fun isValid(username: String): Boolean = PATTERN.matches(username)

    /**
     * 動作確認用の名前か。
     *
     * 接頭辞は小文字ちょうどで一致させる。大文字混じりを受けると
     * `test-1` と `Test-1` が別のアクターとして生えてしまう。
     */
    fun isTest(username: String): Boolean =
        username.startsWith(TEST_PREFIX) &&
            username.length > TEST_PREFIX.length &&
            isValid(username)
}
