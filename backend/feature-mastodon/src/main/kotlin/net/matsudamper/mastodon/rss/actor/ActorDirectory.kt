package net.matsudamper.mastodon.rss.actor

/**
 * リクエストで指定された名前から、どのアクターを指しているかを決める。
 *
 * 引き当てる先は、
 *
 * - 設定で決めた固定アクター（既定 `admin`）
 * - [StoredActorNames] が持っているアクター
 *
 * の 2 つ。どちらでもなければ存在しない。
 *
 * WebFinger の `resource` とパスの `{username}` で判定がずれると、
 * 「検索には出るが開けない」という分かりにくい壊れ方をするので、
 * 両方の入口をここに通す。
 */
class ActorDirectory(
    private val fixed: ActorUrls,
    private val stored: StoredActorNames = StoredActorNames { null },
) {
    private val domain: String = fixed.domain

    /**
     * パスの `{username}` から引く。
     *
     * 固定アクターは大文字小文字を区別しない（Mastodon 側の扱いに合わせる）。
     */
    fun resolve(username: String?): ActorUrls? {
        if (username.isNullOrEmpty()) return null

        if (username.equals(fixed.username, ignoreCase = true)) return fixed

        // 保存されている名前は必ずこの形式なので、引く前に落とせる。
        // `test-1/inbox` のようにパスを含んだものが保存先まで届かなくなる
        if (!ActorUsernameUtil.isValid(username)) return null

        val found = stored.find(username) ?: return null

        return ActorUrls(domain = domain, username = found)
    }

    fun resolve(usernames: Set<String>): Map<String, ActorUrls> {
        if (usernames.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, ActorUrls>()
        val toQuery = mutableSetOf<String>()

        for (username in usernames) {
            if (username.isEmpty()) continue
            if (username.equals(fixed.username, ignoreCase = true)) {
                result[username] = fixed
            } else if (ActorUsernameUtil.isValid(username)) {
                toQuery.add(username)
            }
        }

        if (toQuery.isNotEmpty()) {
            val found = stored.find(toQuery)
            for (username in toQuery) {
                val name = found[username] ?: continue
                result[username] = ActorUrls(domain = domain, username = name)
            }
        }

        return result
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
            // 残りが `feed1/inbox` のようにパスを含んでいたらユーザー名として不正になり、
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
