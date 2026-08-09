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
     * 管理画面。
     *
     * `/admin` の下だけに出す。以前は全パスで管理画面が出ていたので、
     * アカウント画面を開いても管理画面が表示されていた。
     */
    data object Admin : Screen {
        override val path: String = "/$ADMIN_SEGMENT"
        override val title: String = "管理画面 | $SITE_NAME"
    }

    /**
     * アカウント画面。`/@test-1` のように `@` + ユーザー名で開く。
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

        /** 管理画面のパスの先頭。`/admin/password-hash` のような下の階層も管理画面として扱う */
        const val ADMIN_SEGMENT: String = "admin"

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

            if (first == ADMIN_SEGMENT) return Admin

            if (segments.size == 1 && first.startsWith(ACCOUNT_PREFIX)) {
                val username = first.removePrefix(ACCOUNT_PREFIX)
                if (USERNAME_PATTERN.matches(username)) return Account(username)
            }

            return NotFound(path)
        }
    }
}
