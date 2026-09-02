package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.graphql.model.AccountResolver
import net.matsudamper.mastodon.rss.graphql.model.QlAccount
import net.matsudamper.mastodon.rss.graphql.model.QlFeed

class AccountResolverImpl : AccountResolver {
    override fun followerCount(
        account: QlAccount,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<Int>> {
        return GraphQlEngine
            .dataLoaders(env)
            .followerCountDataLoader
            .get(env)
            .load(account.username)
            .thenApply { count ->
                DataFetcherResult.Builder(count ?: 0).build()
            }
    }

    override fun noteCount(
        account: QlAccount,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<Int>> {
        val count = GraphQlEngine.diContainer(env).noteService.noteCount(account.username)
        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder(count.toInt()).build(),
        )
    }

    override fun feed(
        account: QlAccount,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlFeed?>> {
        val feed = GraphQlEngine.diContainer(env).feedService.findByAccountId(account.id)

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder<QlFeed?>(feed?.toGraphqlResponse()).build(),
        )
    }
}
