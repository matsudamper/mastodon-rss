package dev.matsudamper.mastodonrss.actor

/**
 * リクエストで指定された名前から、どのアクターを指しているかを決める。
 *
 * Phase 6 でアクターを DB 駆動にするまでの引き当ては、
 *
 * - 設定で決めた固定アクター（既定 `admin`）
 * - `test-` で始まる使い捨てアクター（[ActorUsername.TEST_PREFIX]）
 *
 * の 2 つだけ。どちらでもなければ存在しない。
 *
 * 使い捨てアクターは常に有効にしている。設定で切り替えられるようにすると、
 * 検証したいときに限って無効なまま 404 を見て悩むことになる。中身は固定アクターと
 * 同じ鍵・同じ内容なので、増えて困るものでもない。
 *
 * WebFinger の `resource` とパスの `{username}` で判定がずれると、
 * 「検索には出るが開けない」という分かりにくい壊れ方をするので、
 * 両方の入口をここに通す。
 */
class ActorDirectory(
    private val fixed: ActorUrls,
) {
    private val domain: String = fixed.domain

    /**
     * パスの `{username}` から引く。
     *
     * 固定アクターは大文字小文字を区別しない（Mastodon 側の扱いに合わせる）。
     * 使い捨てアクターは要求された綴りをそのまま識別子にするので、
     * 接頭辞が小文字ちょうどのものだけを受ける。
     */
    fun resolve(username: String?): ActorUrls? {
        if (username.isNullOrEmpty()) return null

        if (username.equals(fixed.username, ignoreCase = true)) return fixed

        if (ActorUsername.isTest(username)) {
            return ActorUrls(domain = domain, username = username)
        }

        return null
    }

    /**
     * WebFinger の `resource` から引く。
     *
     * Mastodon は `acct:name@domain` で引いてくるが、実装によっては Actor の URL を
     * そのまま渡してくるものもあるので両方を受ける。`acct:` を省いた形も受ける。
     */
    fun resolveResource(resource: String): ActorUrls? {
        val trimmed = resource.trim()
        if (trimmed.isEmpty()) return null

        val actorUrlPrefix = "https://$domain/users/"
        if (trimmed.length > actorUrlPrefix.length && trimmed.startsWith(actorUrlPrefix, ignoreCase = true)) {
            // 残りが `test-1/inbox` のようにパスを含んでいたらユーザー名として不正になり、
            // resolve が弾く（`/` は使える文字に入っていない）
            return resolve(trimmed.substring(actorUrlPrefix.length))
        }

        val withoutScheme =
            if (trimmed.startsWith(ACCT_SCHEME, ignoreCase = true)) {
                trimmed.substring(ACCT_SCHEME.length)
            } else {
                trimmed
            }

        // ユーザー名に @ は使えないので、区切りは最後の @
        val separator = withoutScheme.lastIndexOf('@')
        if (separator <= 0) return null

        val host = withoutScheme.substring(separator + 1)
        if (!host.equals(domain, ignoreCase = true)) return null

        return resolve(withoutScheme.substring(0, separator))
    }

    private companion object {
        const val ACCT_SCHEME = "acct:"
    }
}
