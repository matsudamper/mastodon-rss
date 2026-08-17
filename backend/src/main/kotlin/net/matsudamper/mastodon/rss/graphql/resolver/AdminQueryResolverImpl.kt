package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.GraphqlExceptions
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.graphql.model.AdminQueryResolver
import net.matsudamper.mastodon.rss.graphql.model.QlAdminAccount
import net.matsudamper.mastodon.rss.graphql.model.QlAdminNote
import net.matsudamper.mastodon.rss.graphql.model.QlAdminNotes
import net.matsudamper.mastodon.rss.graphql.model.QlAdminQuery
import net.matsudamper.mastodon.rss.graphql.model.QlAdminSession

class AdminQueryResolverImpl : AdminQueryResolver {
    override fun session(
        adminQuery: QlAdminQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminSession>> {
        val context = GraphQlEngine.graphQlContext(env)
        val adminLoginService = GraphQlEngine.diContainer(env).adminLoginService

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder(
                QlAdminSession(
                    loggedIn = context.isAdminLoggedIn(),
                    passwordConfigured = adminLoginService.adminPasswordConfigured,
                ),
            ).build(),
        )
    }

    override fun account(
        adminQuery: QlAdminQuery,
        username: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminAccount?>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        val account = GraphQlEngine.diContainer(env).accountService.account(username)

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder<QlAdminAccount?>(account?.toGraphqlResponse()).build(),
        )
    }

    override fun notes(
        adminQuery: QlAdminQuery,
        username: String,
        cursor: String?,
        limit: Int?,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminNotes>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        val diContainer = GraphQlEngine.diContainer(env)
        val page = diContainer.noteService.notes(username = username, cursor = cursor, limit = limit)

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder(
                QlAdminNotes(
                    items = page.notes.map { it.toGraphqlResponse(domain = diContainer.domain) },
                    cursor = page.cursor,
                ),
            ).build(),
        )
    }

    override fun accounts(
        adminQuery: QlAdminQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<List<QlAdminAccount>>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        val accounts = GraphQlEngine.diContainer(env).accountService.accounts()

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder(accounts.map { it.toGraphqlResponse() }).build(),
        )
    }
}
