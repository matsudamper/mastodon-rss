package dev.matsudamper.mastodonrss.admin.api

import kotlinx.serialization.Serializable

/**
 * 管理 API のパス。`:backend` のルーティングと `:frontend` の呼び出しで共有する。
 *
 * 管理画面はここより下（[BASE]）に閉じている。ActivityPub のエンドポイントと違って
 * 外から叩かれてよいものではないので、リバースプロキシで塞ぐときの目印にもなる。
 */
object AdminApiPaths {
    /** 管理画面のルート。ここに静的ファイル（Kotlin/Wasm の成果物）を配信する */
    const val BASE: String = "/admin"

    /** 管理 API のルート。画面と同じ prefix に置いて、塞ぐときに 1 か所で済むようにする */
    const val API_BASE: String = "$BASE/api"

    /** ログイン状態の確認 */
    const val SESSION: String = "$API_BASE/session"

    /** ログイン */
    const val LOGIN: String = "$API_BASE/login"

    /** ログアウト */
    const val LOGOUT: String = "$API_BASE/logout"

    /** パスワードハッシュの生成 */
    const val PASSWORD_HASH: String = "$API_BASE/password-hash"

    /** ハッシュ生成画面。ブラウザの URL であって API ではない */
    const val PASSWORD_HASH_PAGE: String = "$BASE/password-hash"
}

/**
 * 管理画面に関わる環境変数の名前。
 *
 * 画面の案内文にそのまま出すので `:frontend` からも要る。実際に環境変数を
 * 読むのは `:backend` の `AdminConfig` で、こちらは名前だけを持つ。
 */
object AdminConfigNames {
    /** パスワードハッシュ。未設定でも起動でき、その場合はログインできない */
    const val ENV_PASSWORD_HASH: String = "ADMIN_PASSWORD_HASH"
}

/**
 * 管理画面のパスワードに課す長さの制限。
 *
 * サーバー側で必ず確かめるが、同じ値を画面でも使って入力前に知らせる。
 */
object AdminPasswordPolicy {
    /**
     * 最短の長さ。総当たりを現実的にしないための下限で、
     * 管理画面のパスワードは人が覚える 1 つだけなので長めにしている。
     */
    const val MIN_LENGTH: Int = 12

    /**
     * 最長の長さ。ハッシュ化は PBKDF2 を数十万回まわすので、
     * 長い入力を投げ続けられると CPU を占有される。上限で頭打ちにする。
     */
    const val MAX_LENGTH: Int = 256
}

/**
 * ログイン状態。
 *
 * @param authenticated ログイン済みなら true
 * @param loginConfigured `ADMIN_PASSWORD_HASH` が設定されていれば true。
 *   false のときはログイン自体ができないので、画面はハッシュ生成の案内を出す
 */
@Serializable
data class AdminSessionResponse(
    val authenticated: Boolean,
    val loginConfigured: Boolean,
)

/**
 * ログイン要求。
 *
 * パスワードを平文で送る。TLS の外側で使うことは想定していない。
 */
@Serializable
data class AdminLoginRequest(
    val password: String,
)

/** パスワードハッシュの生成要求。 */
@Serializable
data class AdminPasswordHashRequest(
    val password: String,
)

/**
 * 生成したパスワードハッシュ。
 *
 * @param environmentVariable 設定先の環境変数名。画面に `NAME=値` の形で出すために返す
 * @param hash 環境変数に入れる値
 */
@Serializable
data class AdminPasswordHashResponse(
    val environmentVariable: String,
    val hash: String,
)

/**
 * エラー応答。
 *
 * 管理画面は運用者しか見ないので、原因が分かる日本語のメッセージをそのまま返す。
 */
@Serializable
data class AdminErrorResponse(
    val message: String,
)
