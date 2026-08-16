package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.GraphqlExceptions
import net.matsudamper.mastodon.rss.graphql.GraphQlContext
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.graphql.model.AdminMutationResolver
import net.matsudamper.mastodon.rss.graphql.model.QlAdminAddAccountFailure
import net.matsudamper.mastodon.rss.graphql.model.QlAdminAddAccountResult
import net.matsudamper.mastodon.rss.graphql.model.QlAdminLoginFailure
import net.matsudamper.mastodon.rss.graphql.model.QlAdminLoginResult
import net.matsudamper.mastodon.rss.graphql.model.QlAdminMutation
import net.matsudamper.mastodon.rss.graphql.model.QlAdminSession
import net.matsudamper.mastodon.rss.logic.AddAccountResult
import net.matsudamper.mastodon.rss.logic.AdminLoginService

class AdminMutationResolverImpl : AdminMutationResolver {
    override fun login(
        adminMutation: QlAdminMutation,
        password: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminLoginResult>> {
        val context = GraphQlEngine.graphQlContext(env)
        val adminLoginService = GraphQlEngine.diContainer(env).adminLoginService

        if (adminLoginService.adminPasswordConfigured.not()) {
            return CompletableFuture.completedFuture(
                DataFetcherResult.Builder(
                    loginFailure(
                        context = context,
                        failure = QlAdminLoginFailure.NOT_CONFIGURED,
                        adminLoginService = adminLoginService,
                    ),
                ).build(),
            )
        }

        if (adminLoginService.matchesAdminPassword(password).not()) {
            return CompletableFuture.completedFuture(
                DataFetcherResult.Builder(
                    loginFailure(
                        context = context,
                        failure = QlAdminLoginFailure.WRONG_PASSWORD,
                        adminLoginService = adminLoginService,
                    ),
                ).build(),
            )
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
        val adminLoginService = GraphQlEngine.diContainer(env).adminLoginService

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder(
                QlAdminSession(
                    loggedIn = false,
                    passwordConfigured = adminLoginService.adminPasswordConfigured,
                ),
            ).build(),
        )
    }

    override fun addAccount(
        adminMutation: QlAdminMutation,
        username: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminAddAccountResult>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        val result = addAccountResult(GraphQlEngine.diContainer(env).accountService.add(username))

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }

    private fun addAccountResult(added: AddAccountResult): QlAdminAddAccountResult {
        return when (added) {
            is AddAccountResult.Success -> {
                QlAdminAddAccountResult(account = added.account.toGraphqlResponse(), failure = null)
            }

            AddAccountResult.InvalidUsername -> {
                QlAdminAddAccountResult(account = null, failure = QlAdminAddAccountFailure.INVALID_USERNAME)
            }

            AddAccountResult.Duplicated -> {
                QlAdminAddAccountResult(account = null, failure = QlAdminAddAccountFailure.DUPLICATED)
            }
        }
    }

    private fun loginFailure(
        context: GraphQlContext,
        failure: QlAdminLoginFailure,
        adminLoginService: AdminLoginService,
    ): QlAdminLoginResult {
        return QlAdminLoginResult(
            session = QlAdminSession(
                loggedIn = context.isAdminLoggedIn(),
                passwordConfigured = adminLoginService.adminPasswordConfigured,
            ),
            failure = failure,
        )
    }
}
