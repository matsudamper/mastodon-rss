package net.matsudamper.mastodon.rss.frontend.logic.admin

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminAccountsQuery
import net.matsudamper.mastodon.rss.frontend.graphql.AdminAddAccountMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminLoginMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminLogoutMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminSessionQuery
import net.matsudamper.mastodon.rss.frontend.graphql.fragment.AdminSessionFields
import net.matsudamper.mastodon.rss.frontend.graphql.type.AdminAddAccountFailure
import net.matsudamper.mastodon.rss.frontend.graphql.type.AdminLoginFailure
import net.matsudamper.mastodon.rss.frontend.logic.GraphQlClient

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

        return AdminAccountsResult.Success(
            data.admin.accounts.map { account ->
                AdminAccount(
                    username = account.username,
                    acct = account.acct,
                    actorUrl = account.actorUrl,
                    createdAt = account.createdAt,
                )
            },
        )
    }

    suspend fun addAccount(username: String): AdminAddAccountResult {
        val response = client.mutation(AdminAddAccountMutation(username)).execute()
        val added = response.data?.admin?.addAccount ?: return AdminAddAccountResult.Failure(response.failureMessage())

        return when (added.failure) {
            null -> {
                val acct = added.account?.acct ?: return AdminAddAccountResult.Failure("追加できたが内容が返ってこない")

                AdminAddAccountResult.Success(acct)
            }

            AdminAddAccountFailure.UNUSABLE_CHARACTER -> {
                AdminAddAccountResult.UnusableCharacter(added.unusableCharacters.orEmpty())
            }

            AdminAddAccountFailure.TOO_LONG -> {
                val maxLength = added.maxLength ?: return AdminAddAccountResult.Failure("上限が返ってこない")

                AdminAddAccountResult.TooLong(maxLength)
            }

            AdminAddAccountFailure.EMPTY -> AdminAddAccountResult.Empty

            AdminAddAccountFailure.DUPLICATED -> AdminAddAccountResult.Duplicated

            AdminAddAccountFailure.UNKNOWN__ -> AdminAddAccountResult.Failure("Unknown")
        }
    }

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
