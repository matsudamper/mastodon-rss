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
 * 管理 API を叩くところ。問い合わせは `:shared:graphql` のスキーマから Apollo が生成する。
 *
 * パスが相対なのは、画面を配信しているオリジンと同じところに投げるため。
 * セッションは `HttpOnly` の Cookie で、同じオリジンならブラウザが勝手に付ける。
 */
class AdminApi(
    private val client: ApolloClient =
        ApolloClient
            .Builder()
            .serverUrl(GRAPHQL_PATH)
            .build(),
) : AutoCloseable {
    /** いまログインしているかを聞く */
    suspend fun session(): AdminSessionResult {
        return client
            .query(AdminSessionQuery())
            .execute()
            .toSessionResult { it.admin.session.adminSessionFields }
    }

    /** サーバーが PBKDF2 を回すぶん応答まで一拍あるので、呼ぶ側は待っている表示を出すこと */
    suspend fun login(password: String): AdminLoginResult {
        val response = client.mutation(AdminLoginMutation(password)).execute()
        val login = response.data?.admin?.login ?: return AdminLoginResult.Failure(response.failureMessage())

        return when (login.failure) {
            null -> AdminLoginResult.Success

            AdminLoginFailure.WRONG_PASSWORD -> AdminLoginResult.WrongPassword

            AdminLoginFailure.NOT_CONFIGURED -> AdminLoginResult.NotConfigured

            // スキーマに理由が増えたが画面がまだ知らない。通ったことにはしない
            AdminLoginFailure.UNKNOWN__ -> AdminLoginResult.Failure("画面が知らない理由で断られた")
        }
    }

    /** ログアウトする。サーバー側のセッションも消える */
    suspend fun logout(): AdminSessionResult {
        return client
            .mutation(AdminLogoutMutation())
            .execute()
            .toSessionResult { it.admin.logout.adminSessionFields }
    }

    override fun close() {
        client.close()
    }

    /**
     * `data` が無いのは繋がらなかったか errors が返ったとき。未ログインとは違うので分ける。
     * 混ぜると、サーバーが落ちているときにパスワードを入れさせることになる。
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

    /** Apollo は例外を投げずに応答へ入れて返すので、通信の失敗も errors もここに来る */
    private fun ApolloResponse<*>.failureMessage(): String {
        return exception?.message
            ?: errors?.joinToString("\n") { it.message }?.takeIf { it.isNotEmpty() }
            ?: "サーバーに繋がらなかった"
    }
}

/** [AdminApi.session] と [AdminApi.logout] の結果 */
sealed interface AdminSessionResult {
    /** @param passwordConfigured 入っていなければログインする手段が無いので、設定方法を出す */
    data class Success(
        val loggedIn: Boolean,
        val passwordConfigured: Boolean,
    ) : AdminSessionResult

    /** ログインしているともしていないとも言えない */
    data class Failure(
        val message: String,
    ) : AdminSessionResult
}

/** [AdminApi.login] の結果 */
sealed interface AdminLoginResult {
    data object Success : AdminLoginResult

    data object WrongPassword : AdminLoginResult

    /** 入力を直しても通らない */
    data object NotConfigured : AdminLoginResult

    data class Failure(
        val message: String,
    ) : AdminLoginResult
}
