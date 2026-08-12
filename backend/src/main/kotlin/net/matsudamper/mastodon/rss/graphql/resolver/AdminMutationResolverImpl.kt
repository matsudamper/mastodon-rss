package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.graphql.GraphQlContext
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.graphql.model.AdminMutationResolver
import net.matsudamper.mastodon.rss.graphql.model.QlAdminLoginFailure
import net.matsudamper.mastodon.rss.graphql.model.QlAdminLoginResult
import net.matsudamper.mastodon.rss.graphql.model.QlAdminMutation
import net.matsudamper.mastodon.rss.graphql.model.QlAdminSession

class AdminMutationResolverImpl : AdminMutationResolver {
    override fun login(
        adminMutation: QlAdminMutation,
        password: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminLoginResult>> {
        val context = GraphQlEngine.graphQlContext(env)

        if (context.adminPasswordConfigured.not()) {
            return CompletableFuture.completedFuture(DataFetcherResult.Builder(loginFailure(context, QlAdminLoginFailure.NOT_CONFIGURED)).build())
        }

        if (context.matchesAdminPassword(password).not()) {
            return CompletableFuture.completedFuture(DataFetcherResult.Builder(loginFailure(context, QlAdminLoginFailure.WRONG_PASSWORD)).build())
        }

        context.issueAdminSession()

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder(
                QlAdminLoginResult(
                    session = QlAdminSession(loggedIn = true, passwordConfigured = true),
                    failure = null,
                ),
            ).build(),
        )
    }

    override fun logout(
        adminMutation: QlAdminMutation,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminSession>> {
        val context = GraphQlEngine.graphQlContext(env)
        context.clearAdminSession()

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(QlAdminSession(loggedIn = false, passwordConfigured = context.adminPasswordConfigured)).build())
    }

    private fun loginFailure(
        context: GraphQlContext,
        failure: QlAdminLoginFailure,
    ): QlAdminLoginResult {
        return QlAdminLoginResult(
            session =
            QlAdminSession(
                loggedIn = context.isAdminLoggedIn(),
                passwordConfigured = context.adminPasswordConfigured,
            ),
            failure = failure,
        )
    }
}
