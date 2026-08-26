package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.GraphqlExceptions
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.graphql.model.AdminAccountResolver
import net.matsudamper.mastodon.rss.graphql.model.QlAdminAccount
import net.matsudamper.mastodon.rss.graphql.model.QlFeed

class AdminAccountResolverImpl : AdminAccountResolver {
    override fun followerCount(
        adminAccount: QlAdminAccount,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<Int>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        return GraphQlEngine
            .dataLoaders(env)
            .followerCountDataLoader
            .get(env)
            .load(adminAccount.account.username)
            .thenApply { count ->
                DataFetcherResult.Builder(count ?: 0).build()
            }
    }

    override fun feed(
        adminAccount: QlAdminAccount,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlFeed?>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        val accountId = adminAccount.account.id
        val feed = GraphQlEngine.diContainer(env).feedService.findByAccountId(accountId)

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder<QlFeed?>(feed?.toGraphqlResponse()).build(),
        )
    }
}
