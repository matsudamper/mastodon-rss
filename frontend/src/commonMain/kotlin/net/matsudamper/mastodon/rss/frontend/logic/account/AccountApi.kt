package net.matsudamper.mastodon.rss.frontend.logic.account

import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Optional
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.watch
import net.matsudamper.mastodon.rss.frontend.graphql.AccountNotesQuery
import net.matsudamper.mastodon.rss.frontend.graphql.AccountScreenQuery
import net.matsudamper.mastodon.rss.frontend.graphql.HomeScreenQuery
import net.matsudamper.mastodon.rss.frontend.graphql.fragment.AccountNoteFields
import net.matsudamper.mastodon.rss.frontend.graphql.type.AccountNotesQuery as AccountNotesQueryInput
import net.matsudamper.mastodon.rss.frontend.logic.GraphQlClient

class AccountApi(
    private val client: ApolloClient = GraphQlClient.apollo,
) {
    suspend fun accounts(cursor: String? = null, limit: Int = 20): AccountsResult {
        val response = client
            .query(HomeScreenQuery(cursor = Optional.presentIfNotNull(cursor), limit = limit))
            .fetchPolicy(FetchPolicy.NetworkOnly)
            .execute()

        if (response.exception != null || response.errors.orEmpty().isNotEmpty()) {
            return AccountsResult.Failure(response.failureMessage())
        }

        val data = response.data ?: return AccountsResult.Failure(response.failureMessage())
        val accounts = data.accounts.nodes.map { account ->
            HomeAccount(
                id = account.id,
                username = account.username,
                acct = account.acct,
            )
        }

        return AccountsResult.Success(
            accounts = accounts,
            hasMore = data.accounts.pageInfo.hasMore,
            nextCursor = data.accounts.pageInfo.nextCursor,
        )
    }

    fun account(username: String): Flow<AccountResult> {
        return client
            .query(AccountScreenQuery(username))
            .fetchPolicy(FetchPolicy.NetworkOnly)
            .watch()
            .map { response -> response.toAccountResult() }
    }

    suspend fun notes(
        username: String,
        cursor: String? = null,
        limit: Int = PAGE_SIZE,
    ): AccountNotesResult {
        val response = client
            .query(
                AccountNotesQuery(
                    query = AccountNotesQueryInput(
                        username = username,
                        cursor = Optional.presentIfNotNull(cursor),
                        limit = limit,
                    ),
                ),
            )
            .fetchPolicy(FetchPolicy.NetworkOnly)
            .execute()

        if (response.exception != null || response.errors.orEmpty().isNotEmpty()) {
            return AccountNotesResult.Failure(response.failureMessage())
        }

        val data = response.data ?: return AccountNotesResult.Failure(response.failureMessage())
        val notes = data.notes

        return AccountNotesResult.Success(
            notes = notes.nodes.map { it.accountNoteFields.toAccountNote() },
            cursor = notes.pageInfo.nextCursor,
        )
    }

    private fun ApolloResponse<AccountScreenQuery.Data>.toAccountResult(): AccountResult {
        if (exception != null || errors.orEmpty().isNotEmpty()) {
            return AccountResult.Failure(failureMessage())
        }

        val data = data ?: return AccountResult.Failure(failureMessage())
        val account = data.account ?: return AccountResult.NotFound

        return AccountResult.Success(
            account = Account(
                id = account.id,
                username = account.username,
                acct = account.acct,
                actorUrl = account.actorUrl,
            ),
            followerCount = account.followerCount,
            noteCount = account.noteCount,
            feed = account.feed?.let { feed ->
                AccountFeed(
                    feedUrl = feed.url,
                    siteUrl = feed.siteUrl,
                )
            },
        )
    }

    private fun AccountNoteFields.toAccountNote(): AccountNote = AccountNote(
        url = url,
        contentHtml = contentHtml,
        publishedAt = Instant.fromEpochSeconds(publishedAt),
    )

    private fun ApolloResponse<*>.failureMessage(): String {
        return exception?.message
            ?: errors?.joinToString("\n") { it.message }?.takeIf { it.isNotEmpty() }
            ?: "ネットワークエラー"
    }

    private companion object {
        const val PAGE_SIZE: Int = 20
    }
}
