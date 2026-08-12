package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.graphql.GraphQlContext
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine.Companion.graphQlContext
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
        val context = env.graphQlContext()

        if (context.adminPasswordConfigured.not()) {
            return completed(context.loginFailure(QlAdminLoginFailure.NOT_CONFIGURED))
        }

        if (context.matchesAdminPassword(password).not()) {
            return completed(context.loginFailure(QlAdminLoginFailure.WRONG_PASSWORD))
        }

        context.issueAdminSession()

        return completed(
            QlAdminLoginResult(
                session = QlAdminSession(loggedIn = true, passwordConfigured = true),
                failure = null,
            ),
        )
    }

    override fun logout(
        adminMutation: QlAdminMutation,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminSession>> {
        val context = env.graphQlContext()
        context.clearAdminSession()

        return completed(
            QlAdminSession(loggedIn = false, passwordConfigured = context.adminPasswordConfigured),
        )
    }

    private fun GraphQlContext.loginFailure(failure: QlAdminLoginFailure): QlAdminLoginResult {
        return QlAdminLoginResult(
            session = toQlAdminSession(),
            failure = failure,
        )
    }
}
