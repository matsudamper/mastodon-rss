package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.GraphqlExceptions
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.graphql.model.AdminNoteResolver
import net.matsudamper.mastodon.rss.graphql.model.QlAdminFeedItem
import net.matsudamper.mastodon.rss.graphql.model.QlAdminNote

class AdminNoteResolverImpl : AdminNoteResolver {
    override fun feedItem(
        adminNote: QlAdminNote,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminFeedItem?>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        return GraphQlEngine
            .dataLoaders(env)
            .feedItemByNoteDataLoader
            .get(env)
            .load(adminNote.id)
            .thenApply { item ->
                DataFetcherResult.Builder<QlAdminFeedItem?>(item?.toGraphqlResponse()).build()
            }
    }
}
