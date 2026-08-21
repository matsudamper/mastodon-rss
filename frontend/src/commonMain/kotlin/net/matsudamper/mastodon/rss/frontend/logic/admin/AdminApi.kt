package net.matsudamper.mastodon.rss.frontend.logic.admin

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Optional
import net.matsudamper.mastodon.rss.frontend.graphql.AdminAccountQuery
import net.matsudamper.mastodon.rss.frontend.graphql.AdminAccountsQuery
import net.matsudamper.mastodon.rss.frontend.graphql.AdminAddAccountMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminLoginMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminLogoutMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminSessionQuery
import net.matsudamper.mastodon.rss.frontend.graphql.fragment.AdminAccountFields
import net.matsudamper.mastodon.rss.frontend.graphql.fragment.AdminSessionFields
import net.matsudamper.mastodon.rss.frontend.graphql.type.AdminAccountsInput
import net.matsudamper.mastodon.rss.frontend.graphql.type.AdminLoginFailure
import net.matsudamper.mastodon.rss.frontend.logic.GraphQlClient
import net.matsudamper.mastodon.rss.frontend.logic.account.Account

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

    suspend fun accounts(cursor: String? = null, limit: Int = PAGE_SIZE): AdminAccountsResult {
        val response =
            client
                .query(
                    AdminAccountsQuery(
                        input =
                        Optional.present(
                            AdminAccountsInput(
                                cursor = Optional.presentIfNotNull(cursor),
                                limit = Optional.present(limit),
                            ),
                        ),
                    ),
                )
                .execute()

        if (response.exception != null || response.errors.orEmpty().isNotEmpty()) {
            return AdminAccountsResult.Failure(response.failureMessage())
        }

        val data = response.data ?: return AdminAccountsResult.Failure(response.failureMessage())
        val connection = data.admin.adminAccounts

        return AdminAccountsResult.Success(
            accounts = connection.nodes.map { it.adminAccountFields.toAdminAccount() },
            hasMore = connection.pageInfo.hasMore,
            nextCursor = connection.pageInfo.nextCursor,
        )
    }

    suspend fun account(username: String): AdminAccountResult {
        val response = client.query(AdminAccountQuery(username)).execute()

        // 失敗を先に見る。null は「そのアカウントが無い」の意味なので、
        // エラーで返ってきた null と混ぜると、繋がらないだけの状態を
        // アカウントが無いと表示してしまう
        if (response.exception != null || response.errors.orEmpty().isNotEmpty()) {
            return AdminAccountResult.Failure(response.failureMessage())
        }

        val data = response.data ?: return AdminAccountResult.Failure(response.failureMessage())

        return AdminAccountResult.Success(data.admin.adminAccount?.adminAccountFields?.toAdminAccount())
    }

    suspend fun addAccount(username: String): AdminAddAccountResult {
        val response = client.mutation(AdminAddAccountMutation(username)).execute()
        val added = response.data?.admin?.addAccount ?: return AdminAddAccountResult.Failure(response.failureMessage())

        val failure = added.failure
            ?: return AdminAddAccountResult.Success(
                added.adminAccount?.account?.acct ?: return AdminAddAccountResult.Failure("追加できたが内容が返ってこない"),
            )

        return AdminAddAccountResult.Rejected(
            unusableCharacters = failure.unusableCharacters.orEmpty(),
            maxLength = failure.maxLength,
            minLength = failure.minLength,
            isDuplicated = failure.isDuplicated,
        )
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

    private fun AdminAccountFields.toAdminAccount(): AdminAccount = AdminAccount(
        account = Account(
            username = account.username,
            acct = account.acct,
            actorUrl = account.actorUrl,
        ),
        deletable = deletable,
        createdAt = createdAt,
    )

    private companion object {
        const val PAGE_SIZE = 20
    }
}
