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
import net.matsudamper.mastodon.rss.frontend.graphql.AccountFollowersQuery
import net.matsudamper.mastodon.rss.frontend.graphql.AccountNoteQuery
import net.matsudamper.mastodon.rss.frontend.graphql.AccountNotesQuery
import net.matsudamper.mastodon.rss.frontend.graphql.AccountScreenQuery
import net.matsudamper.mastodon.rss.frontend.graphql.HomeScreenQuery
import net.matsudamper.mastodon.rss.frontend.graphql.fragment.AccountNoteFields
import net.matsudamper.mastodon.rss.frontend.graphql.type.AccountFollowersQuery as AccountFollowersQueryInput
import net.matsudamper.mastodon.rss.frontend.graphql.type.AccountNotesQuery as AccountNotesQueryInput
import net.matsudamper.mastodon.rss.frontend.logic.CachedPaging
import net.matsudamper.mastodon.rss.frontend.logic.GraphQlClient
import net.matsudamper.mastodon.rss.frontend.logic.Paging

class AccountApi(
    private val client: ApolloClient = GraphQlClient.apollo,
) {
    fun accounts(limit: Int): Paging<AccountsResult> {
        return CachedPaging(
            client = client,
            firstPage = HomeScreenQuery(cursor = Optional.absent(), limit = limit),
            nextPage = { cursor -> HomeScreenQuery(cursor = Optional.present(cursor), limit = limit) },
            appendPage = { cached, fetched ->
                cached.copy(
                    accounts = cached.accounts.copy(
                        nodes = cached.accounts.nodes + fetched.accounts.nodes,
                        pageInfo = fetched.accounts.pageInfo,
                    ),
                )
            },
            toResult = { response -> response.toAccountsResult() },
        )
    }

    fun account(username: String): Flow<AccountResult> {
        return client
            .query(AccountScreenQuery(username))
            .fetchPolicy(FetchPolicy.NetworkOnly)
            .watch()
            .map { response -> response.toAccountResult() }
    }

    fun notes(username: String, limit: Int): Paging<AccountNotesResult> {
        return CachedPaging(
            client = client,
            firstPage = AccountNotesQuery(
                query = AccountNotesQueryInput(
                    username = username,
                    cursor = Optional.absent(),
                    limit = limit,
                ),
            ),
            nextPage = { cursor ->
                AccountNotesQuery(
                    query = AccountNotesQueryInput(
                        username = username,
                        cursor = Optional.present(cursor),
                        limit = limit,
                    ),
                )
            },
            appendPage = { cached, fetched ->
                cached.copy(
                    notes = cached.notes.copy(
                        nodes = cached.notes.nodes + fetched.notes.nodes,
                        pageInfo = fetched.notes.pageInfo,
                    ),
                )
            },
            toResult = { response -> response.toAccountNotesResult() },
        )
    }

    fun followers(username: String, limit: Int): Paging<AccountFollowersResult> {
        return CachedPaging(
            client = client,
            firstPage = AccountFollowersQuery(
                query = AccountFollowersQueryInput(
                    username = username,
                    cursor = Optional.absent(),
                    limit = limit,
                ),
            ),
            nextPage = { cursor ->
                AccountFollowersQuery(
                    query = AccountFollowersQueryInput(
                        username = username,
                        cursor = Optional.present(cursor),
                        limit = limit,
                    ),
                )
            },
            appendPage = { cached, fetched ->
                val cachedFollowers = cached.followers
                val fetchedFollowers = fetched.followers
                if (cachedFollowers == null || fetchedFollowers == null) {
                    // アカウントが消えた。足せるものが無いので取ってきた方をそのまま流す
                    fetched
                } else {
                    cached.copy(
                        followers = cachedFollowers.copy(
                            nodes = cachedFollowers.nodes + fetchedFollowers.nodes,
                            pageInfo = fetchedFollowers.pageInfo,
                        ),
                    )
                }
            },
            toResult = { response -> response.toAccountFollowersResult() },
        )
    }

    suspend fun note(username: String, id: String): AccountNoteResult {
        val response = client
            .query(AccountNoteQuery(username = username, id = id))
            .fetchPolicy(FetchPolicy.NetworkOnly)
            .execute()

        if (response.exception != null || response.errors.orEmpty().isNotEmpty()) {
            return AccountNoteResult.Failure(response.failureMessage())
        }

        val data = response.data ?: return AccountNoteResult.Failure(response.failureMessage())
        val note = data.note ?: return AccountNoteResult.NotFound
        return AccountNoteResult.Success(note.accountNoteFields.toAccountNote())
    }

    private fun ApolloResponse<HomeScreenQuery.Data>.toAccountsResult(): AccountsResult {
        if (exception != null || errors.orEmpty().isNotEmpty()) {
            return AccountsResult.Failure(failureMessage())
        }

        val data = data ?: return AccountsResult.Failure(failureMessage())

        return AccountsResult.Success(
            accounts = data.accounts.nodes.map { account ->
                HomeAccount(
                    id = account.id,
                    username = account.username,
                    acct = account.acct,
                    displayName = account.displayName,
                    iconUrl = account.iconUrl,
                )
            },
            hasMore = data.accounts.pageInfo.hasMore,
            nextCursor = data.accounts.pageInfo.nextCursor,
        )
    }

    private fun ApolloResponse<AccountNotesQuery.Data>.toAccountNotesResult(): AccountNotesResult {
        if (exception != null || errors.orEmpty().isNotEmpty()) {
            return AccountNotesResult.Failure(failureMessage())
        }

        val data = data ?: return AccountNotesResult.Failure(failureMessage())

        return AccountNotesResult.Success(
            notes = data.notes.nodes.map { it.accountNoteFields.toAccountNote() },
            cursor = data.notes.pageInfo.nextCursor,
        )
    }

    private fun ApolloResponse<AccountFollowersQuery.Data>.toAccountFollowersResult(): AccountFollowersResult {
        if (exception != null || errors.orEmpty().isNotEmpty()) {
            return AccountFollowersResult.Failure(failureMessage())
        }

        val data = data ?: return AccountFollowersResult.Failure(failureMessage())
        val followers = data.followers ?: return AccountFollowersResult.NotFound

        return AccountFollowersResult.Success(
            followers = followers.nodes.map { AccountFollower(url = it.url, acct = it.acct) },
            nextCursor = followers.pageInfo.nextCursor,
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
                displayName = account.displayName,
                summary = account.summary,
            ),
            iconUrl = account.iconUrl,
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
        id = id,
        url = url,
        contentHtml = contentHtml,
        publishedAt = Instant.fromEpochSeconds(publishedAt),
    )

    private fun ApolloResponse<*>.failureMessage(): String {
        return exception?.message
            ?: errors?.joinToString("\n") { it.message }?.takeIf { it.isNotEmpty() }
            ?: "ネットワークエラー"
    }
}
