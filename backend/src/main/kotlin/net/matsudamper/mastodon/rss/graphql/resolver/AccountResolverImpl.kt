package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.graphql.model.AccountResolver
import net.matsudamper.mastodon.rss.graphql.model.QlAccount

class AccountResolverImpl : AccountResolver {
    override fun displayName(
        account: QlAccount,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<String>> =
        loadProfile(account, env) { profile ->
            DataFetcherResult.Builder(profile.displayName).build()
        }

    override fun summary(
        account: QlAccount,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<String>> =
        loadProfile(account, env) { profile ->
            DataFetcherResult.Builder(profile.summary).build()
        }

    override fun profileStored(
        account: QlAccount,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<Boolean>> =
        loadProfile(account, env) { profile ->
            DataFetcherResult.Builder(profile.stored).build()
        }

    private fun <T> loadProfile(
        account: QlAccount,
        env: DataFetchingEnvironment,
        map: (net.matsudamper.mastodon.rss.logic.AccountService.ResolvedProfile) -> DataFetcherResult<T>,
    ): CompletionStage<DataFetcherResult<T>> =
        GraphQlEngine
            .dataLoaders(env)
            .accountProfileDataLoader
            .get(env)
            .load(account.username)
            .thenApply { profile ->
                checkNotNull(profile) { "プロフィールを引けなかった: ${account.username}" }
                map(profile)
            }
}
