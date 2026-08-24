package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.graphql.data.AccountsCursor
import net.matsudamper.mastodon.rss.graphql.data.NotesCursor
import net.matsudamper.mastodon.rss.graphql.model.QlAccount
import net.matsudamper.mastodon.rss.graphql.model.QlAccountNote
import net.matsudamper.mastodon.rss.graphql.model.QlAccountNotesConnection
import net.matsudamper.mastodon.rss.graphql.model.QlAccountNotesQuery
import net.matsudamper.mastodon.rss.graphql.model.QlAccountsConnection
import net.matsudamper.mastodon.rss.graphql.model.QlAdminQuery
import net.matsudamper.mastodon.rss.graphql.model.QlPageInfo
import net.matsudamper.mastodon.rss.graphql.model.QueryResolver

class QueryResolverImpl : QueryResolver {
    override fun admin(env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlAdminQuery>> {
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(QlAdminQuery()).build())
    }

    override fun accounts(
        cursor: String?,
        limit: Int,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAccountsConnection>> {
        val after = cursor?.let { AccountsCursor.decode(it) }

        val connection = if (cursor != null && after == null) {
            QlAccountsConnection(
                nodes = emptyList(),
                pageInfo = QlPageInfo(hasMore = false, nextCursor = null),
            )
        } else {
            val result = GraphQlEngine
                .diContainer(env)
                .accountService
                .accounts(
                    afterUsername = after?.afterUsername,
                    limit = limit.coerceIn(0, MAX_ACCOUNTS_LIMIT),
                )

            QlAccountsConnection(
                nodes = result.accounts.map { it.urls.toGraphqlResponse(it.accountId) },
                pageInfo = QlPageInfo(
                    hasMore = result.hasMore,
                    nextCursor = result.nextUsername?.let { AccountsCursor(afterUsername = it).encode() },
                ),
            )
        }

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder(connection).build(),
        )
    }

    override fun account(
        username: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAccount?>> {
        return GraphQlEngine
            .dataLoaders(env)
            .accountDataLoader
            .get(env)
            .load(username)
            .thenApply { account ->
                DataFetcherResult.Builder<QlAccount?>(
                    account?.let { it.urls.toGraphqlResponse(it.accountId) },
                ).build()
            }
    }

    override fun notes(
        query: QlAccountNotesQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAccountNotesConnection>> {
        val cursor = query.cursor?.let { NotesCursor.decode(it) }

        val connection = if (query.cursor != null && cursor == null) {
            QlAccountNotesConnection(
                nodes = emptyList(),
                pageInfo = QlPageInfo(hasMore = false, nextCursor = null),
            )
        } else {
            val page = GraphQlEngine.diContainer(env).noteService.noteIds(
                username = query.username,
                after = cursor?.toPosition(),
                limit = query.limit,
            )

            QlAccountNotesConnection(
                nodes = page.ids.map { QlAccountNote(id = it) },
                pageInfo = QlPageInfo(
                    hasMore = page.hasMore,
                    nextCursor = page.nextPosition?.let { NotesCursor.of(it).encode() },
                ),
            )
        }

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder(connection).build(),
        )
    }

    private companion object {
        const val MAX_ACCOUNTS_LIMIT = 100
    }
}
