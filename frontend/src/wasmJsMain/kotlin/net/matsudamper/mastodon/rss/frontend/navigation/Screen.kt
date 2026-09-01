package net.matsudamper.mastodon.rss.frontend.navigation

import androidx.navigation3.runtime.NavKey

/**
 * 画面のパス。Navigation 3 のバックスタックに積むキーでもある。
 *
 * サーバーは自分が持つパス以外を全部 `index.html` に落とすので、どの画面を出すかは
 * ブラウザ側で決めることになる。判定をここに集めておかないと、リンクを張る側と
 * 画面を出す側で綴りがずれて「リンクは踏めるが真っ白になる」壊れ方をする。
 *
 * Phase 8 で `:shared` を作ったら、パスの定数はそちらに移してサーバーと共有する。
 */
sealed interface Screen : NavKey {
    /** ブラウザのアドレスバーに出すパス */
    val path: String

    /** `document.title` に入れる文字列 */
    val title: String

    /** トップ。何をするサーバーなのかと、各画面への入口だけを置く */
    data object Home : Screen {
        override val path: String = "/"
        override val title: String = SITE_NAME
    }

    /**
     * 管理画面のトップ。
     *
     * `/admin` の下だけに出す。以前は全パスで管理画面が出ていたので、
     * アカウント画面を開いても管理画面が表示されていた。
     *
     * ログインと、管理画面の中の各画面への入口だけを置く。操作そのものは
     * 下の階層に分ける。1 つの画面に並べると、開いた時点で必要のない
     * 問い合わせまで走り、URL でその操作を指せなくなる。
     */
    data object Admin : Screen {
        override val path: String = "/$ADMIN_SEGMENT"
        override val title: String = "管理画面 | $SITE_NAME"
    }

    /**
     * アカウントの一覧
     */
    data object AdminAccounts : Screen {
        override val path: String = "/$ADMIN_SEGMENT/$ACCOUNTS_SEGMENT"
        override val title: String = "アカウント | $SITE_NAME"
    }

    /**
     * アカウントの追加
     */
    data object AdminAccountNew : Screen {
        override val path: String = "/$ADMIN_SEGMENT/$ACCOUNTS_SEGMENT/$NEW_SEGMENT"
        override val title: String = "アカウントの追加 | $SITE_NAME"
    }

    /**
     * アカウント 1 つの管理画面。
     *
     * そのアカウントとしての操作はここに集める。いまは投稿と、配信した投稿の一覧。
     */
    data class AdminAccount(
        val username: String,
    ) : Screen {
        // 名前の前に `@` を置く。追加の画面と同じ階層に並ぶので、これが無いと
        // `new` という名前のアカウントを開けない
        override val path: String = "/$ADMIN_SEGMENT/$ACCOUNTS_SEGMENT/$ACCOUNT_PREFIX$username"
        override val title: String = "@$username の管理 | $SITE_NAME"
    }

    /**
     * アカウント画面。`/@feed1` のように `@` + ユーザー名で開く。
     *
     * ActivityPub の Actor JSON を返す `/users/{name}` とはパスを分ける。
     * 同じパスで Accept ヘッダを見て HTML と JSON を出し分ける手もあるが、
     * 相手の実装によって Accept の綴りが揺れるため、外すとアカウントごと
     * 見つからなくなる。Mastodon 自身も `/@name` と `/users/name` で分けている。
     */
    data class Account(
        val username: String,
        val noteId: String? = null,
    ) : Screen {
        override val path: String = buildString {
            append("/$ACCOUNT_PREFIX$username")
            noteId?.let { append("/$it") }
        }
        override val title: String = if (noteId == null) {
            "@$username | $SITE_NAME"
        } else {
            "@$username の投稿 | $SITE_NAME"
        }
    }

    /**
     * 知らないパス。
     *
     * [path] は要求されたパスのまま持つ。ここで `/` に書き換えると、
     * 戻るボタンで元のパスに戻れなくなる。
     */
    data class NotFound(
        override val path: String,
    ) : Screen {
        override val title: String = "見つからない | $SITE_NAME"
    }

    companion object {
        const val SITE_NAME: String = "mastodon-rss"

        /**
         * 管理画面のパスの先頭
         */
        const val ADMIN_SEGMENT: String = "admin"

        private const val ACCOUNTS_SEGMENT: String = "accounts"

        private const val NEW_SEGMENT: String = "new"

        /** アカウント画面の目印。ユーザー名に `@` は使えないので、これで一意に判別できる */
        const val ACCOUNT_PREFIX: String = "@"

        /**
         * `@name` の形のセグメントから名前を取り出す。名前が入っていなければ null。
         *
         * 名前として通るかどうかはここでは見ない。見るとサーバーの規則を画面側にも
         * 持つことになり、片方だけ変えたときに API では引けるのに画面だけ見つからない、
         * という食い違いが出る。実在するかどうかと同じく、開いた先の画面が
         * サーバーに聞く。
         */
        private fun accountNameOf(segment: String): String? {
            if (!segment.startsWith(ACCOUNT_PREFIX)) return null

            return segment.removePrefix(ACCOUNT_PREFIX).ifEmpty { null }
        }

        /**
         * `window.location.pathname` から画面を決める。
         *
         * @param path 先頭が `/` のパス。クエリとハッシュは含めない
         */
        fun of(path: String): Screen {
            val segments = path.split('/').filter { it.isNotEmpty() }

            val first = segments.firstOrNull() ?: return Home

            if (first == ADMIN_SEGMENT) {
                // 知らない下の階層は管理画面ではなく見つからない扱いにする。
                // 綴りを間違えたリンクで管理画面が出ると、間違いに気付けない
                val rest = segments.drop(1)

                return when {
                    rest.isEmpty() -> Admin

                    rest == listOf(ACCOUNTS_SEGMENT) -> AdminAccounts

                    rest == listOf(ACCOUNTS_SEGMENT, NEW_SEGMENT) -> AdminAccountNew

                    rest.size == 2 && rest[0] == ACCOUNTS_SEGMENT -> {
                        accountNameOf(rest[1])?.let { AdminAccount(it) } ?: NotFound(path)
                    }

                    else -> NotFound(path)
                }
            }

            if (segments.size in 1..2) {
                accountNameOf(first)?.let { username ->
                    return Account(username = username, noteId = segments.getOrNull(1))
                }
            }

            return NotFound(path)
        }
    }
}
