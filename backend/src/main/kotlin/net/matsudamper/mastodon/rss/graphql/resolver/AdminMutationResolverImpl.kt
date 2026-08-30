package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.GraphqlExceptions
import net.matsudamper.mastodon.rss.actor.ActorUsernameUtil
import net.matsudamper.mastodon.rss.graphql.GraphQlContext
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.graphql.model.AdminMutationResolver
import net.matsudamper.mastodon.rss.graphql.model.QlAdminAddAccountFailure
import net.matsudamper.mastodon.rss.graphql.model.QlAdminAddAccountResult
import net.matsudamper.mastodon.rss.graphql.model.QlAdminDeleteFeedItemsResult
import net.matsudamper.mastodon.rss.graphql.model.QlAdminDeleteNoteFailure
import net.matsudamper.mastodon.rss.graphql.model.QlAdminDeleteNoteFailureReason
import net.matsudamper.mastodon.rss.graphql.model.QlAdminDeleteNoteResult
import net.matsudamper.mastodon.rss.graphql.model.QlAdminLoginFailure
import net.matsudamper.mastodon.rss.graphql.model.QlAdminLoginResult
import net.matsudamper.mastodon.rss.graphql.model.QlAdminMutation
import net.matsudamper.mastodon.rss.graphql.model.QlAdminNote
import net.matsudamper.mastodon.rss.graphql.model.QlAdminPostFeedItemsResult
import net.matsudamper.mastodon.rss.graphql.model.QlAdminPostNoteFailure
import net.matsudamper.mastodon.rss.graphql.model.QlAdminPostNoteResult
import net.matsudamper.mastodon.rss.graphql.model.QlAdminSaveFeedResult
import net.matsudamper.mastodon.rss.graphql.model.QlAdminSession
import net.matsudamper.mastodon.rss.graphql.model.QlDeleteFeedItemsQuery
import net.matsudamper.mastodon.rss.graphql.model.QlDeleteNoteQuery
import net.matsudamper.mastodon.rss.graphql.model.QlPostFeedItemsQuery
import net.matsudamper.mastodon.rss.graphql.model.QlSaveFeedQuery
import net.matsudamper.mastodon.rss.logic.AccountService
import net.matsudamper.mastodon.rss.logic.AdminLoginService
import net.matsudamper.mastodon.rss.logic.FeedService
import net.matsudamper.mastodon.rss.logic.NoteService
import net.matsudamper.mastodon.rss.repository.entity.FeedItemId
import net.matsudamper.mastodon.rss.shared.PublicNoteId
import net.matsudamper.mastodon.rss.telemetry.withOpenTelemetryContext

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
                QlAdminAddAccountResult(adminAccount = added.account.toGraphqlResponse(), failure = null)
            }

            is AccountService.AddAccountResult.Failure -> {
                QlAdminAddAccountResult(adminAccount = null, failure = added.toGraphqlResponse())
            }
        }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }

    /**
     * 配信の成否は投稿の成否と別に返す。相手のサーバーが受け取らなくても
     * こちらの記録は残るので、どちらも分かる形にしないと画面で説明できない
     */
    override fun postNote(
        adminMutation: QlAdminMutation,
        username: String,
        body: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminPostNoteResult>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        val diContainer = GraphQlEngine.diContainer(env)

        // 配信は相手のサーバーへの POST を伴うので中断できる形で呼ぶ。
        // GraphQL のリゾルバは CompletionStage を返す約束なので、そこに繋ぎ直す
        return CoroutineScope(Dispatchers.IO.withOpenTelemetryContext()).future {
            val result = when (val posted = diContainer.noteService.post(username = username, body = body)) {
                is NoteService.PostResult.Success -> {
                    QlAdminPostNoteResult(
                        note = QlAdminNote(
                            id = PublicNoteId(posted.published.publicId),
                            url = posted.published.url,
                            contentHtml = posted.published.contentHtml,
                            publishedAt = posted.published.publishedAt.epochSecond,
                        ),
                        deliveryTargets = posted.published.targets,
                        delivered = posted.published.delivered,
                        failure = null,
                    )
                }

                is NoteService.PostResult.Failure -> {
                    QlAdminPostNoteResult(
                        note = null,
                        deliveryTargets = null,
                        delivered = null,
                        failure = QlAdminPostNoteFailure(
                            unknownAccount = posted.unknownAccount,
                            isEmpty = posted.isEmpty,
                            maxLength = NoteService.MAX_LENGTH.takeIf { posted.tooLong },
                        ),
                    )
                }
            }

            DataFetcherResult.Builder(result).build()
        }
    }

    override fun saveFeed(
        adminMutation: QlAdminMutation,
        saveFeedQuery: QlSaveFeedQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminSaveFeedResult>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        val diContainer = GraphQlEngine.diContainer(env)

        return CoroutineScope(Dispatchers.IO.withOpenTelemetryContext()).future {
            val result = diContainer.feedService.save(
                accountId = saveFeedQuery.accountId,
                url = saveFeedQuery.url,
            ).toGraphqlResponse()
            DataFetcherResult.Builder(result).build()
        }
    }

    override fun postFeedItems(
        adminMutation: QlAdminMutation,
        query: QlPostFeedItemsQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminPostFeedItemsResult>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        val diContainer = GraphQlEngine.diContainer(env)

        return CoroutineScope(Dispatchers.IO.withOpenTelemetryContext()).future {
            val result = diContainer.feedService.postUnpublished(
                accountId = query.accountId,
            ).toGraphqlResponse()
            DataFetcherResult.Builder(result).build()
        }
    }

    override fun deleteFeedItems(
        adminMutation: QlAdminMutation,
        query: QlDeleteFeedItemsQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminDeleteFeedItemsResult>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        val result = GraphQlEngine.diContainer(env).feedService.deleteItems(
            accountId = query.accountId,
            feedItemIds = query.feedItemIds.map { FeedItemId(it.value) },
        ).toGraphqlResponse()

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }

    override fun deleteNote(
        adminMutation: QlAdminMutation,
        query: QlDeleteNoteQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminDeleteNoteResult>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        val diContainer = GraphQlEngine.diContainer(env)

        return CoroutineScope(Dispatchers.IO.withOpenTelemetryContext()).future {
            val result = when (
                val deleted = diContainer.noteService.delete(
                    username = query.username,
                    publicId = query.noteId.value,
                )
            ) {
                is NoteService.DeleteResult.Success -> QlAdminDeleteNoteResult(
                    deletedId = PublicNoteId(deleted.deleted.publicId),
                    failure = null,
                )

                is NoteService.DeleteResult.Failure -> QlAdminDeleteNoteResult(
                    deletedId = null,
                    failure = QlAdminDeleteNoteFailure(
                        reason = when (deleted.reason) {
                            NoteService.DeleteFailure.UNKNOWN_ACCOUNT ->
                                QlAdminDeleteNoteFailureReason.UNKNOWN_ACCOUNT

                            NoteService.DeleteFailure.NOT_FOUND ->
                                QlAdminDeleteNoteFailureReason.NOT_FOUND
                        },
                    ),
                )
            }

            DataFetcherResult.Builder(result).build()
        }
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
