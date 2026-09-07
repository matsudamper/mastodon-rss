package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.actor.ActorUrls
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
     *
     * 取得元が変わったことが分かるよう、その URL から決まる値をクエリに付ける。
     * パスだけだとアイコンを差し替えても URL が変わらず、画面側と
     * ブラウザが前の画像を出し続ける
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
                val source = feed?.iconUrl
                val iconUrl = if (source == null) {
                    null
                } else {
                    "${account.actorUrl}${ActorUrls.ICON_PATH}?v=${ActorUrls.iconVersion(source)}"
                }
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
