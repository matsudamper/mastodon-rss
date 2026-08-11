package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult

/**
 * リゾルバの戻り値を組み立てる。
 *
 * 生成されたインタフェースが `CompletionStage` を返す形なのは、フィールドごとに
 * 別のスレッドへ逃がせるようにするため。こちらは逃がさず、呼び出し元のスレッドで
 * 済ませて完了済みの `CompletionStage` を返す。
 *
 * 口（`graphQlRoutes`）が実行そのものを `Dispatchers.IO` に載せているので、
 * ここで別のプールを用意すると、待つ場所が 2 段になるだけで何も速くならない。
 * ログインの PBKDF2 もその `Dispatchers.IO` の上で回る。
 */
internal fun <T : Any> completed(value: T): CompletionStage<DataFetcherResult<T>> {
    return CompletableFuture.completedFuture(
        DataFetcherResult
            .newResult<T>()
            .data(value)
            .build(),
    )
}
