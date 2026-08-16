package net.matsudamper.mastodon.rss.frontend.api

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminAccountsQuery
import net.matsudamper.mastodon.rss.frontend.graphql.AdminAddAccountMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminLoginMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminLogoutMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminSessionQuery
import net.matsudamper.mastodon.rss.frontend.graphql.fragment.AdminAccountFields
import net.matsudamper.mastodon.rss.frontend.graphql.fragment.AdminSessionFields
import net.matsudamper.mastodon.rss.frontend.graphql.type.AdminAddAccountFailure
import net.matsudamper.mastodon.rss.frontend.graphql.type.AdminLoginFailure

class AdminApi(
    private val client: ApolloClient = GraphQlClient.apollo,
) {
    suspend fun session(): AdminSessionResult {
        return client
            .query(AdminSessionQuery())
            .execute()
            .toSessionResult { it.admin.session.adminSessionFields }
    }

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

    suspend fun logout(): AdminSessionResult {
        return client
            .mutation(AdminLogoutMutation())
            .execute()
            .toSessionResult { it.admin.logout.adminSessionFields }
    }

    suspend fun accounts(): AdminAccountsResult {
        val response = client.query(AdminAccountsQuery()).execute()
        val data = response.data ?: return AdminAccountsResult.Failure(response.failureMessage())

        return AdminAccountsResult.Success(data.admin.accounts.map { it.adminAccountFields.toAccount() })
    }

    suspend fun addAccount(username: String): AdminAddAccountResult {
        val response = client.mutation(AdminAddAccountMutation(username)).execute()
        val added = response.data?.admin?.addAccount ?: return AdminAddAccountResult.Failure(response.failureMessage())

        return when (added.failure) {
            null -> {
                val account =
                    added.account?.adminAccountFields
                        ?: return AdminAddAccountResult.Failure("追加できたが内容が返ってこない")

                AdminAddAccountResult.Success(account.toAccount())
            }

            AdminAddAccountFailure.INVALID_USERNAME -> AdminAddAccountResult.InvalidUsername

            AdminAddAccountFailure.DUPLICATED -> AdminAddAccountResult.Duplicated

            AdminAddAccountFailure.UNKNOWN__ -> AdminAddAccountResult.Failure("Unknown")
        }
    }

    private fun AdminAccountFields.toAccount(): AdminAccount =
        AdminAccount(
            username = username,
            acct = acct,
            actorUrl = actorUrl,
            createdAt = createdAt,
        )

    /**
     * `data` が無いのは失敗。ログインしていない状態と混ぜない
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

    private fun ApolloResponse<*>.failureMessage(): String {
        return exception?.message
            ?: errors?.joinToString("\n") { it.message }?.takeIf { it.isNotEmpty() }
            ?: "ネットワークエラー"
    }
}

sealed interface AdminSessionResult {
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

    data object NotConfigured : AdminLoginResult

    data class Failure(
        val message: String,
    ) : AdminLoginResult
}

/**
 * @param createdAt 追加した時刻。エポックからの秒数。設定で決まるアカウントには無い
 */
data class AdminAccount(
    val username: String,
    val acct: String,
    val actorUrl: String,
    val createdAt: Long?,
)

sealed interface AdminAccountsResult {
    data class Success(
        val accounts: List<AdminAccount>,
    ) : AdminAccountsResult

    data class Failure(
        val message: String,
    ) : AdminAccountsResult
}

sealed interface AdminAddAccountResult {
    data class Success(
        val account: AdminAccount,
    ) : AdminAddAccountResult

    data object InvalidUsername : AdminAddAccountResult

    data object Duplicated : AdminAddAccountResult

    data class Failure(
        val message: String,
    ) : AdminAddAccountResult
}
