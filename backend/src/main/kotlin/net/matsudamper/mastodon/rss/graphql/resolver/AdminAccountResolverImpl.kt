package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.GraphqlExceptions
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.graphql.data.FailedDeliveriesCursor
import net.matsudamper.mastodon.rss.graphql.data.RetryingDeliveriesCursor
import net.matsudamper.mastodon.rss.graphql.model.AdminAccountResolver
import net.matsudamper.mastodon.rss.graphql.model.QlAdminAccount
import net.matsudamper.mastodon.rss.graphql.model.QlAdminDeliveryQueueCounts
import net.matsudamper.mastodon.rss.graphql.model.QlAdminFailedDeliveriesConnection
import net.matsudamper.mastodon.rss.graphql.model.QlAdminFailedDelivery
import net.matsudamper.mastodon.rss.graphql.model.QlAdminRetryingDeliveriesConnection
import net.matsudamper.mastodon.rss.graphql.model.QlAdminRetryingDelivery
import net.matsudamper.mastodon.rss.graphql.model.QlFeed
import net.matsudamper.mastodon.rss.graphql.model.QlPageInfo

class AdminAccountResolverImpl : AdminAccountResolver {
    override fun followerCount(
        adminAccount: QlAdminAccount,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<Int>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        return GraphQlEngine
            .dataLoaders(env)
            .followerCountDataLoader
            .get(env)
            .load(adminAccount.account.username)
            .thenApply { count ->
                DataFetcherResult.Builder(count ?: 0).build()
            }
    }

    override fun feed(
        adminAccount: QlAdminAccount,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlFeed?>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        val accountId = adminAccount.account.id
        val feed = GraphQlEngine.diContainer(env).feedService.findByAccountId(accountId)

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder<QlFeed?>(feed?.toGraphqlResponse()).build(),
        )
    }

    override fun deliveryQueue(
        adminAccount: QlAdminAccount,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminDeliveryQueueCounts>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        val counts = GraphQlEngine.diContainer(env).deliveryQueueService.counts(adminAccount.account.username)

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder(
                QlAdminDeliveryQueueCounts(
                    waitingCount = counts.waiting.toInt(),
                    failedCount = counts.failed.toInt(),
                ),
            ).build(),
        )
    }

    override fun retryingDeliveries(
        adminAccount: QlAdminAccount,
        cursor: String?,
        limit: Int,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminRetryingDeliveriesConnection>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        val position = cursor?.let { RetryingDeliveriesCursor.decode(it) }

        // 読めないカーソルは、消えた行を指していたのと同じ扱いにする
        val connection = if (cursor != null && position == null) {
            QlAdminRetryingDeliveriesConnection(
                nodes = emptyList(),
                pageInfo = QlPageInfo(hasMore = false, nextCursor = null),
            )
        } else {
            val page = GraphQlEngine.diContainer(env).deliveryQueueService.retrying(
                username = adminAccount.account.username,
                after = position?.toPosition(),
                limit = limit,
            )

            QlAdminRetryingDeliveriesConnection(
                nodes = page.deliveries.map { delivery ->
                    QlAdminRetryingDelivery(
                        inbox = delivery.inbox,
                        attempts = delivery.attempts,
                        nextAttemptAt = delivery.nextAttemptAt.epochSecond,
                        lastError = delivery.lastError,
                    )
                },
                pageInfo = QlPageInfo(
                    hasMore = page.hasMore,
                    nextCursor = page.nextPosition?.let { RetryingDeliveriesCursor.of(it).encode() },
                ),
            )
        }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(connection).build())
    }

    override fun failedDeliveries(
        adminAccount: QlAdminAccount,
        cursor: String?,
        limit: Int,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminFailedDeliveriesConnection>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        val decoded = cursor?.let { FailedDeliveriesCursor.decode(it) }

        // 読めないカーソルは、消えた行を指していたのと同じ扱いにする
        val connection = if (cursor != null && decoded == null) {
            QlAdminFailedDeliveriesConnection(
                nodes = emptyList(),
                pageInfo = QlPageInfo(hasMore = false, nextCursor = null),
            )
        } else {
            val page = GraphQlEngine.diContainer(env).deliveryQueueService.failed(
                username = adminAccount.account.username,
                afterId = decoded?.toDeliveryId(),
                limit = limit,
            )

            QlAdminFailedDeliveriesConnection(
                nodes = page.deliveries.map { delivery ->
                    QlAdminFailedDelivery(
                        inbox = delivery.inbox,
                        attempts = delivery.attempts,
                        lastError = delivery.lastError,
                    )
                },
                pageInfo = QlPageInfo(
                    hasMore = page.hasMore,
                    nextCursor = page.nextId?.let { FailedDeliveriesCursor.of(it).encode() },
                ),
            )
        }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(connection).build())
    }
}
