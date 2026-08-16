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
     * アカウント画面。`/@feed1` のように `@` + ユーザー名で開く。
     *
     * ActivityPub の Actor JSON を返す `/users/{name}` とはパスを分ける。
     * 同じパスで Accept ヘッダを見て HTML と JSON を出し分ける手もあるが、
     * 相手の実装によって Accept の綴りが揺れるため、外すとアカウントごと
     * 見つからなくなる。Mastodon 自身も `/@name` と `/users/name` で分けている。
     */
    data class Account(
        val username: String,
    ) : Screen {
        override val path: String = "/$ACCOUNT_PREFIX$username"
        override val title: String = "@$username | $SITE_NAME"
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
         * アクターのユーザー名に使える文字。
         *
         * `:backend` の `ActorUsername` と同じ規則。ここで通してもサーバーが
         * 知らない名前なら中身は出ないが、`/@` だけや `/@a/b` のような
         * そもそもアカウントを指していないパスは画面を出す前に落とす。
         *
         * サーバーと二重に持っているのは、モジュール間で共有する置き場
         * （`:shared`）がまだ無いため。Phase 8 で作ったらそちらに寄せる。
         */
        private val USERNAME_PATTERN = Regex("^[A-Za-z0-9_]([A-Za-z0-9_.-]*[A-Za-z0-9_])?$")

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
                return when (segments.drop(1)) {
                    emptyList<String>() -> Admin
                    listOf(ACCOUNTS_SEGMENT) -> AdminAccounts
                    listOf(ACCOUNTS_SEGMENT, NEW_SEGMENT) -> AdminAccountNew
                    else -> NotFound(path)
                }
            }

            if (segments.size == 1 && first.startsWith(ACCOUNT_PREFIX)) {
                val username = first.removePrefix(ACCOUNT_PREFIX)
                if (USERNAME_PATTERN.matches(username)) return Account(username)
            }

            return NotFound(path)
        }
    }
}
