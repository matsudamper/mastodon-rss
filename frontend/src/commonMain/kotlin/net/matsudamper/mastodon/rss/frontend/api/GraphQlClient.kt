package net.matsudamper.mastodon.rss.frontend.api

import com.apollographql.apollo.ApolloClient
import net.matsudamper.mastodon.rss.shared.GRAPHQL_PATH

object GraphQlClient {
    val apollo: ApolloClient =
        ApolloClient
            .Builder()
            .serverUrl(GRAPHQL_PATH)
            .build()
}
