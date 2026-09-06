package net.matsudamper.mastodon.rss.graphql.resolver

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
        return GraphQlEngine
            .dataLoaders(env)
            .noteCountDataLoader
            .get(env)
            .load(account.username)
            .thenApply { count ->
                DataFetcherResult.Builder(count ?: 0).build()
            }
    }

    /**
     * 中身を返すのはこの GraphQL ではなく Actor と同じ `/users/{username}/icon`。
     * Mastodon に渡しているものと同じ URL を返して、出るものを揃える
     */
    override fun iconUrl(
        account: QlAccount,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<String?>> {
        return GraphQlEngine
            .dataLoaders(env)
            .feedByAccountIdDataLoader
            .get(env)
            .load(account.id)
            .thenApply { feed ->
                val iconUrl = if (feed?.iconUrl == null) null else "${account.actorUrl}/icon"
                DataFetcherResult.Builder<String?>(iconUrl).build()
            }
    }

    override fun feed(
        account: QlAccount,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlFeed?>> {
        return GraphQlEngine
            .dataLoaders(env)
            .feedByAccountIdDataLoader
            .get(env)
            .load(account.id)
            .thenApply { feed ->
                DataFetcherResult.Builder<QlFeed?>(feed?.toGraphqlResponse()).build()
            }
    }
}
