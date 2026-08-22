package net.[REDACTED].mastodon.rss.frontend.logic.account

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Optional
import kotlin.time.Instant
import net.[REDACTED].mastodon.rss.frontend.graphql.AccountNotesQuery
import net.[REDACTED].mastodon.rss.frontend.graphql.AccountScreenQuery
import net.[REDACTED].mastodon.rss.frontend.graphql.HomeScreenQuery
import net.[REDACTED].mastodon.rss.frontend.graphql.fragment.AccountNoteFields
import net.[REDACTED].mastodon.rss.frontend.logic.GraphQlClient

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

    suspend fun account(username: String, notesLimit: Int = PAGE_SIZE): AccountResult {
        val response = client
            .query(
                AccountScreenQuery(
                    username = username,
                    notesCursor = Optional.absent(),
                    notesLimit = notesLimit,
                ),
            ).execute()

        if (response.exception != null || response.errors.orEmpty().isNotEmpty()) {
            return AccountResult.Failure(response.failureMessage())
        }

        val data = response.data ?: return AccountResult.Failure(response.failureMessage())
        val account = data.account ?: return AccountResult.NotFound
        val notes = account.notes

        return AccountResult.Success(
            account = Account(
                username = account.username,
                acct = account.acct,
                actorUrl = account.actorUrl,
            ),
            notes = notes.nodes.map { it.accountNoteFields.toAccountNote() },
            notesCursor = notes.pageInfo.nextCursor,
        )
    }

    suspend fun notes(
        username: String,
        cursor: String? = null,
        limit: Int = PAGE_SIZE,
    ): AccountNotesResult {
        val response = client
            .query(
                AccountNotesQuery(
                    username = username,
                    cursor = Optional.presentIfNotNull(cursor),
                    limit = limit,
                ),
            ).execute()

        if (response.exception != null || response.errors.orEmpty().isNotEmpty()) {
            return AccountNotesResult.Failure(response.failureMessage())
        }

        val data = response.data ?: return AccountNotesResult.Failure(response.failureMessage())
        val notes = data.account?.notes ?: return AccountNotesResult.Failure("投稿を取れなかった")

        return AccountNotesResult.Success(
            notes = notes.nodes.map { it.accountNoteFields.toAccountNote() },
            cursor = notes.pageInfo.nextCursor,
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
