package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult

/**
 * 別のスレッドへは逃がさない。`graphQlRoutes` が実行そのものを `Dispatchers.IO` に載せている
 */
internal fun <T : Any> completed(value: T): CompletionStage<DataFetcherResult<T>> {
    return CompletableFuture.completedFuture(
        DataFetcherResult
            .newResult<T>()
            .data(value)
            .build(),
    )
}
