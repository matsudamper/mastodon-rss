package net.matsudamper.mastodon.rss.frontend.logic.account

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import net.matsudamper.mastodon.rss.frontend.graphql.AccountQuery
import net.matsudamper.mastodon.rss.frontend.logic.GraphQlClient

class AccountApi(
    private val client: ApolloClient = GraphQlClient.apollo,
) {
    suspend fun account(username: String): AccountResult {
        val response = client.query(AccountQuery(username)).execute()
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
