package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.GraphqlExceptions
import net.matsudamper.mastodon.rss.actor.ActorUsernameUtil
import net.matsudamper.mastodon.rss.graphql.GraphQlContext
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.graphql.model.AdminMutationResolver
import net.matsudamper.mastodon.rss.graphql.model.QlAdminAddAccountFailure
import net.matsudamper.mastodon.rss.graphql.model.QlAdminAddAccountResult
import net.matsudamper.mastodon.rss.graphql.model.QlAdminLoginFailure
import net.matsudamper.mastodon.rss.graphql.model.QlAdminLoginResult
import net.matsudamper.mastodon.rss.graphql.model.QlAdminMutation
import net.matsudamper.mastodon.rss.graphql.model.QlAdminSession
import net.matsudamper.mastodon.rss.logic.AccountService
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

        val result = when (val added = GraphQlEngine.diContainer(env).accountService.add(username)) {
            is AccountService.AddAccountResult.Success -> {
                QlAdminAddAccountResult(account = added.account.toGraphqlResponse(), failure = null)
            }

            is AccountService.AddAccountResult.Failure -> {
                QlAdminAddAccountResult(account = null, failure = added.toGraphqlResponse())
            }
        }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }

    /**
     * 当てはまらない理由は null にする。入っているものだけが理由になる
     */
    private fun AccountService.AddAccountResult.Failure.toGraphqlResponse(): QlAdminAddAccountFailure =
        QlAdminAddAccountFailure(
            unusableCharacters = unusableCharacters.map { it.toString() }.takeIf { it.isNotEmpty() },
            maxLength = ActorUsernameUtil.MAX_LENGTH.takeIf { tooLong },
            minLength = ActorUsernameUtil.MIN_LENGTH.takeIf { tooShort },
            isDuplicated = duplicated,
        )

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
