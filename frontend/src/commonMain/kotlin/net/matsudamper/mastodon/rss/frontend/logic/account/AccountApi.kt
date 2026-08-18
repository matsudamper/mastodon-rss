package net.matsudamper.mastodon.rss.frontend.logic.account

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Optional
import net.matsudamper.mastodon.rss.frontend.graphql.AccountScreenQuery
import net.matsudamper.mastodon.rss.frontend.graphql.HomeScreenQuery
import net.matsudamper.mastodon.rss.frontend.logic.GraphQlClient

class AccountApi(
    private val client: ApolloClient = GraphQlClient.apollo,
) {
    suspend fun accounts(cursor: String? = null, limit: Int = 20): AccountsResult {
        val response = client.query(HomeScreenQuery(cursor = Optional.presentIfNotNull(cursor), limit = limit)).execute()

        if (response.exception != null || response.errors.orEmpty().isNotEmpty()) {
            return AccountsResult.Failure(response.failureMessage())
        }

        val data = response.data ?: return AccountsResult.Failure(response.failureMessage())
        val accounts = data.accounts.nodes.map { account ->
            Account(
                username = account.username,
                acct = account.acct,
                actorUrl = account.actorUrl,
            )
        }

        return AccountsResult.Success(
            accounts = accounts,
            hasMore = data.accounts.pageInfo.hasMore,
            nextCursor = data.accounts.pageInfo.nextCursor,
        )
    }

    suspend fun account(username: String): AccountResult {
        val response = client.query(AccountScreenQuery(username)).execute()

        if (response.exception != null || response.errors.orEmpty().isNotEmpty()) {
            return AccountResult.Failure(response.failureMessage())
        }

        val data = response.data ?: return AccountResult.Failure(response.failureMessage())
        val account = data.account ?: return AccountResult.NotFound

        return AccountResult.Success(
            Account(
                username = account.username,
                acct = account.acct,
                actorUrl = account.actorUrl,
            ),
        )
    }

    private fun ApolloResponse<*>.failureMessage(): String {
        return exception?.message
            ?: errors?.joinToString("\n") { it.message }?.takeIf { it.isNotEmpty() }
            ?: "ネットワークエラー"
    }
}
