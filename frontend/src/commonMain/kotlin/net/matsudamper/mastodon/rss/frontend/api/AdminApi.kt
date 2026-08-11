package net.matsudamper.mastodon.rss.frontend.api

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminLoginMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminLogoutMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminSessionQuery
import net.matsudamper.mastodon.rss.frontend.graphql.fragment.AdminSessionFields
import net.matsudamper.mastodon.rss.frontend.graphql.type.AdminLoginFailure

/** GraphQL を受けるパス。サーバーが登録しているものと同じ */
private const val GRAPHQL_PATH = "/graphql"

/**
 * 管理画面から管理 API を叩くところ。
 *
 * 口は `/graphql` の 1 つで、問い合わせは `:shared:graphql` のスキーマから
 * Apollo が生成したものを使う。パスを相対で書いているのは、画面を配信している
 * オリジンと同じところに投げるため。開発サーバー (8081) から動かす場合は、
 * webpack の devServer が `/graphql` を backend (8080) に転送する
 * （`webpack.config.d/dev-server-proxy.js`）。
 *
 * セッションは `HttpOnly` の Cookie なので、ここからは読めないし持ち回りもしない。
 * 同じオリジンへのリクエストにはブラウザが勝手に付ける。
 */
class AdminApi(
    private val client: ApolloClient =
        ApolloClient
            .Builder()
            .serverUrl(GRAPHQL_PATH)
            .build(),
) : AutoCloseable {
    /** いまログインしているかを聞く */
    suspend fun session(): AdminSessionResult =
        client
            .query(AdminSessionQuery())
            .execute()
            .toSessionResult { it.admin.session.adminSessionFields }

    /**
     * パスワードを送る。通れば Cookie が返り、以降のリクエストに付く。
     *
     * サーバーは PBKDF2 を 21 万回まわしてから返すので、応答まで一拍ある。
     * 呼ぶ側は待っている間の表示を出すこと。
     */
    suspend fun login(password: String): AdminLoginResult {
        val response = client.mutation(AdminLoginMutation(password)).execute()
        val login = response.data?.admin?.login ?: return AdminLoginResult.Failure(response.failureMessage())

        return when (login.failure) {
            null -> AdminLoginResult.Success

            // 入れ直せば通る
            AdminLoginFailure.WRONG_PASSWORD -> AdminLoginResult.WrongPassword

            // サーバーに ADMIN_PASSWORD_HASH が入っていない。パスワードの問題ではない
            AdminLoginFailure.NOT_CONFIGURED -> AdminLoginResult.NotConfigured

            // スキーマに理由が増えたが画面がまだ知らない。通ったことにはしない
            AdminLoginFailure.UNKNOWN__ -> AdminLoginResult.Failure("画面が知らない理由で断られた")
        }
    }

    /** ログアウトする。サーバー側のセッションも消える */
    suspend fun logout(): AdminSessionResult =
        client
            .mutation(AdminLogoutMutation())
            .execute()
            .toSessionResult { it.admin.logout.adminSessionFields }

    override fun close() {
        client.close()
    }

    /**
     * 応答からセッションの状態を取り出す。
     *
     * `data` が無いのは、繋がらなかったか、サーバーが GraphQL のエラーを返したとき。
     * どちらも「ログインしていない」とは違うので分けて返す。ここで未ログイン扱いに
     * すると、サーバーが落ちているときにパスワードを入れさせることになる。
     */
    private fun <D : Operation.Data> ApolloResponse<D>.toSessionResult(
        select: (D) -> AdminSessionFields,
    ): AdminSessionResult {
        val data = data ?: return AdminSessionResult.Failure(failureMessage())
        val session = select(data)

        return AdminSessionResult.Success(
            loggedIn = session.loggedIn,
            passwordConfigured = session.passwordConfigured,
        )
    }

    /**
     * 失敗の理由。
     *
     * Apollo は例外を投げずに応答へ入れて返すので、繋がらなかった場合も
     * サーバーが errors を返した場合もここに来る。
     */
    private fun ApolloResponse<*>.failureMessage(): String =
        exception?.message
            ?: errors?.joinToString("\n") { it.message }?.takeIf { it.isNotEmpty() }
            ?: "サーバーに繋がらなかった"
}

/** [AdminApi.session] と [AdminApi.logout] の結果 */
sealed interface AdminSessionResult {
    /**
     * 状態を取れた。
     *
     * @param passwordConfigured サーバーに `ADMIN_PASSWORD_HASH` が入っているか。
     *   入っていなければログインする手段が無いので、画面には設定方法を出す
     */
    data class Success(
        val loggedIn: Boolean,
        val passwordConfigured: Boolean,
    ) : AdminSessionResult

    /** 状態が分からなかった。ログインしているともしていないとも言えない */
    data class Failure(
        val message: String,
    ) : AdminSessionResult
}

/** [AdminApi.login] の結果 */
sealed interface AdminLoginResult {
    data object Success : AdminLoginResult

    /** パスワードが違う。入力を直せば通る */
    data object WrongPassword : AdminLoginResult

    /** サーバーに `ADMIN_PASSWORD_HASH` が無い。入力を直しても通らない */
    data object NotConfigured : AdminLoginResult

    data class Failure(
        val message: String,
    ) : AdminLoginResult
}
