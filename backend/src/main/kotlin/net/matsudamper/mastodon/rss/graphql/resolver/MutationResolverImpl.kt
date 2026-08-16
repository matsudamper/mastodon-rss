package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.graphql.model.MutationResolver
import net.matsudamper.mastodon.rss.graphql.model.QlAdminMutation

class MutationResolverImpl : MutationResolver {
    override fun admin(env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlAdminMutation>> {
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(QlAdminMutation()).build())
    }
}
