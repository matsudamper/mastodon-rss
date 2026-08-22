package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.GraphqlExceptions
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.graphql.data.AccountsCursor
import net.matsudamper.mastodon.rss.graphql.data.NotesCursor
import net.matsudamper.mastodon.rss.graphql.model.AdminQueryResolver
import net.matsudamper.mastodon.rss.graphql.model.QlAdminAccount
import net.matsudamper.mastodon.rss.graphql.model.QlAdminAccountsConnection
import net.matsudamper.mastodon.rss.graphql.model.QlAdminAccountsInput
import net.matsudamper.mastodon.rss.graphql.model.QlAdminNotesConnection
import net.matsudamper.mastodon.rss.graphql.model.QlAdminQuery
import net.matsudamper.mastodon.rss.graphql.model.QlAdminSession
import net.matsudamper.mastodon.rss.graphql.model.QlPageInfo

class AdminQueryResolverImpl : AdminQueryResolver {
    override fun session(
        adminQuery: QlAdminQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminSession>> {
        val context = GraphQlEngine.graphQlContext(env)
        val adminLoginService = GraphQlEngine.diContainer(env).adminLoginService

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder(
                QlAdminSession(
                    loggedIn = context.isAdminLoggedIn(),
                    passwordConfigured = adminLoginService.adminPasswordConfigured,
                ),
            ).build(),
        )
    }

    override fun adminAccounts(
        adminQuery: QlAdminQuery,
        input: QlAdminAccountsInput?,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminAccountsConnection>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        val cursor = input?.cursor
        val after = cursor?.let { AccountsCursor.decode(it) }

        val connection = if (cursor != null && after == null) {
            QlAdminAccountsConnection(
                nodes = emptyList(),
                pageInfo = QlPageInfo(hasMore = false, nextCursor = null),
            )
        } else {
            val result = GraphQlEngine
                .diContainer(env)
                .accountService
                .accounts(
                    afterUsername = after?.afterUsername,
                    limit = (input?.limit ?: DEFAULT_LIMIT).coerceIn(0, MAX_ADMIN_ACCOUNTS_LIMIT),
                )

            QlAdminAccountsConnection(
                nodes = result.accounts.map { it.toGraphqlResponse() },
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

    override fun adminAccount(
        adminQuery: QlAdminQuery,
        username: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminAccount?>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        val account = GraphQlEngine.diContainer(env).accountService.account(username)

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder<QlAdminAccount?>(account?.toGraphqlResponse()).build(),
        )
    }

    override fun notes(
        adminQuery: QlAdminQuery,
        username: String,
        cursor: String?,
        limit: Int,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminNotesConnection>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        // カーソルを組み立てるのも解くのもこの層。下は位置しか知らない
        val after = cursor?.let { NotesCursor.decode(it) }

        // 読めないカーソルは、消えた投稿を指していたのと同じ扱いにする
        val connection = if (cursor != null && after == null) {
            QlAdminNotesConnection(
                nodes = emptyList(),
                pageInfo = QlPageInfo(hasMore = false, nextCursor = null),
            )
        } else {
            val diContainer = GraphQlEngine.diContainer(env)
            val page = diContainer.noteService.notes(
                username = username,
                after = after?.toPosition(),
                limit = limit,
            )

            QlAdminNotesConnection(
                nodes = page.notes.map { it.toGraphqlResponse(domain = diContainer.domain) },
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
        const val DEFAULT_LIMIT = 10
        const val MAX_ADMIN_ACCOUNTS_LIMIT = 50
    }
}
