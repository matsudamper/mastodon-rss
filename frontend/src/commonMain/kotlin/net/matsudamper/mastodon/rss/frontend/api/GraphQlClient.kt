package net.matsudamper.mastodon.rss.frontend.api

import com.apollographql.apollo.ApolloClient
import net.matsudamper.mastodon.rss.shared.GRAPHQL_PATH

/**
 * 口は管理画面に限らないので、画面ごとの API ではなくここが持つ。
 * 画面を閉じても閉じない
 */
object GraphQlClient {
    val apollo: ApolloClient =
        ApolloClient
            .Builder()
            .serverUrl(GRAPHQL_PATH)
            .build()
}
