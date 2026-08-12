package net.matsudamper.mastodon.rss.frontend.api

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminLoginMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminLogoutMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminSessionQuery
import net.matsudamper.mastodon.rss.frontend.graphql.fragment.AdminSessionFields
import net.matsudamper.mastodon.rss.frontend.graphql.type.AdminLoginFailure

/**
 * パスが相対なのは、画面を配信しているオリジンと同じところに投げるため。
 * セッションは `HttpOnly` の Cookie で、同じオリジンならブラウザが勝手に付ける。
 */
class AdminApi(
    private val client: ApolloClient =
        ApolloClient
            .Builder()
            .serverUrl("/graphql")
            .build(),
) : AutoCloseable {
    suspend fun session(): AdminSessionResult {
        return client
            .query(AdminSessionQuery())
            .execute()
            .toSessionResult { it.admin.session.adminSessionFields }
    }

    /**
     * サーバーが PBKDF2 を回すぶん応答まで一拍あるので、呼ぶ側は待っている表示を出すこと
     */
    suspend fun login(password: String): AdminLoginResult {
        val response = client.mutation(AdminLoginMutation(password)).execute()
        val login = response.data?.admin?.login ?: return AdminLoginResult.Failure(response.failureMessage())

        return when (login.failure) {
            null -> AdminLoginResult.Success
            AdminLoginFailure.WRONG_PASSWORD -> AdminLoginResult.WrongPassword
            AdminLoginFailure.NOT_CONFIGURED -> AdminLoginResult.NotConfigured
            AdminLoginFailure.UNKNOWN__ -> AdminLoginResult.Failure("Unknown")
        }
    }

    /**
     * サーバー側のセッションも消える
     */
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
     * `data` が無いのは繋がらなかったか errors が返ったとき。未ログインと混ぜると、
     * サーバーが落ちているときにパスワードを入れさせることになる。
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
     * Apollo は例外を投げずに応答へ入れて返す
     */
    private fun ApolloResponse<*>.failureMessage(): String {
        return exception?.message
            ?: errors?.joinToString("\n") { it.message }?.takeIf { it.isNotEmpty() }
            ?: "サーバーに繋がらなかった"
    }
}

sealed interface AdminSessionResult {
    /**
     * @param passwordConfigured false ならログインする手段が無いので、設定方法を出す
     */
    data class Success(
        val loggedIn: Boolean,
        val passwordConfigured: Boolean,
    ) : AdminSessionResult

    data class Failure(
        val message: String,
    ) : AdminSessionResult
}

sealed interface AdminLoginResult {
    data object Success : AdminLoginResult

    data object WrongPassword : AdminLoginResult

    /**
     * 入力を直しても通らない
     */
    data object NotConfigured : AdminLoginResult

    data class Failure(
        val message: String,
    ) : AdminLoginResult
}
