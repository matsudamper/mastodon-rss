package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.graphql.model.QlAccount
import net.matsudamper.mastodon.rss.graphql.model.QlAdminQuery
import net.matsudamper.mastodon.rss.graphql.model.QueryResolver

class QueryResolverImpl : QueryResolver {
    override fun admin(env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlAdminQuery>> {
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(QlAdminQuery()).build())
    }

    /**
     * 名前が無ければ null を返す。エラーにしないのは、名前を間違えただけの状態と
     * 引けなかった状態を画面が区別できるようにするため
     */
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
}
