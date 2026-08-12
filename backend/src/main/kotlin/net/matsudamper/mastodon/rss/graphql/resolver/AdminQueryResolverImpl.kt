package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine.Companion.graphQlContext
import net.matsudamper.mastodon.rss.graphql.model.AdminQueryResolver
import net.matsudamper.mastodon.rss.graphql.model.QlAdminQuery
import net.matsudamper.mastodon.rss.graphql.model.QlAdminSession

class AdminQueryResolverImpl : AdminQueryResolver {
    override fun session(
        adminQuery: QlAdminQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminSession>> {
        return completed(env.graphQlContext().toQlAdminSession())
    }
}
