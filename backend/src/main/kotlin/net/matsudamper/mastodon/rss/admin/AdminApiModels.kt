package net.matsudamper.mastodon.rss.admin

import kotlinx.serialization.Serializable

/**
 * 管理画面のログイン API でやり取りする型。
 *
 * フィードの一覧や登録といった管理 API は GraphQL（`/graphql`）に寄せる予定だが、
 * ログインとセッションの確認はそこには入れない。認証が無いと叩けない口の中に
 * 認証そのものを置くと、未ログインでも通す例外をスキーマ側に作ることになる。
 *
 * 同じ形を `:frontend` の `api/AdminApi.kt` にも書いている。両方から使える
 * 置き場（`:shared`）がまだ無いため。作ったらそちらに移す。
 */
@Serializable
data class AdminLoginRequest(
    val password: String,
)

/**
 * ログインの状態。ログイン後の画面と、ログインの口を出すかどうかの判断に使う。
 *
 * @param passwordConfigured `ADMIN_PASSWORD_HASH` が設定されているか。
 *   未設定のときはログインしようがないので、画面には設定方法を出す
 */
@Serializable
data class AdminSessionResponse(
    val loggedIn: Boolean,
    val passwordConfigured: Boolean,
)

/** 失敗したときの理由。画面にそのまま出す */
@Serializable
data class AdminErrorResponse(
    val message: String,
)
