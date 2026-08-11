package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.graphql.model.QlAdminQuery
import net.matsudamper.mastodon.rss.graphql.model.QueryResolver

class QueryResolverImpl : QueryResolver {
    override fun admin(env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlAdminQuery>> {
        return completed(QlAdminQuery())
    }
}
