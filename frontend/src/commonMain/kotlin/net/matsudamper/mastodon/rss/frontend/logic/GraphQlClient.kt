package net.matsudamper.mastodon.rss.frontend.logic

import com.apollographql.apollo.ApolloClient
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import net.matsudamper.mastodon.rss.frontend.graphql.cache.Cache.cache
import net.matsudamper.mastodon.rss.shared.GRAPHQL_PATH

object GraphQlClient {
    private const val CACHE_SIZE_BYTES = 10 * 1024 * 1024

    val apollo: ApolloClient =
        ApolloClient
            .Builder()
            .serverUrl(GRAPHQL_PATH)
            .cache(MemoryCacheFactory(maxSizeBytes = CACHE_SIZE_BYTES))
            .build()
}
