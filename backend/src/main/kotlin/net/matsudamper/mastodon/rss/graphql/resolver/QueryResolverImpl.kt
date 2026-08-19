package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.graphql.data.AccountsCursor
import net.matsudamper.mastodon.rss.graphql.model.QlAccount
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
        // カーソルを組み立てるのも解くのもこの層。下は名前しか知らない
        val after = cursor?.let { AccountsCursor.decode(it) }

        // 読めないカーソルは、消えたアカウントを指していたのと同じ扱いにする
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
                nodes = result.accounts.map { it.urls.toGraphqlResponse() },
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
            .thenApply { urls ->
                DataFetcherResult.Builder<QlAccount?>(urls?.toGraphqlResponse()).build()
            }
    }

    private companion object {
        /**
         * 1 回に返す上限。取得側が limit + 1 を数えるので、Int があふれる値を先に落とす
         */
        const val MAX_ACCOUNTS_LIMIT = 100
    }
}
