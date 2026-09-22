package net.matsudamper.mastodon.rss.graphql.resolver

import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.graphql.data.AccountsCursor
import net.matsudamper.mastodon.rss.graphql.data.FollowersCursor
import net.matsudamper.mastodon.rss.graphql.data.NotesCursor
import net.matsudamper.mastodon.rss.graphql.model.QlAccount
import net.matsudamper.mastodon.rss.graphql.model.QlAccountFollower
import net.matsudamper.mastodon.rss.graphql.model.QlAccountFollowersConnection
import net.matsudamper.mastodon.rss.graphql.model.QlAccountFollowersQuery
import net.matsudamper.mastodon.rss.graphql.model.QlAccountNote
import net.matsudamper.mastodon.rss.graphql.model.QlAccountNotesConnection
import net.matsudamper.mastodon.rss.graphql.model.QlAccountNotesQuery
import net.matsudamper.mastodon.rss.graphql.model.QlAccountsConnection
import net.matsudamper.mastodon.rss.graphql.model.QlAdminQuery
import net.matsudamper.mastodon.rss.graphql.model.QlPageInfo
import net.matsudamper.mastodon.rss.graphql.model.QlTimelineQuery
import net.matsudamper.mastodon.rss.graphql.model.QueryResolver
import net.matsudamper.mastodon.rss.remoteactor.RemoteActorIconUrls
import net.matsudamper.mastodon.rss.repository.StoredFollower
import net.matsudamper.mastodon.rss.shared.PublicNoteId

class QueryResolverImpl : QueryResolver {
    override fun admin(env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlAdminQuery>> {
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(QlAdminQuery()).build())
    }

    override fun accounts(
        cursor: String?,
        limit: Int,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAccountsConnection>> {
        val after = cursor?.let { AccountsCursor.decode(it) }

        val connection = if (cursor != null && after == null) {
            QlAccountsConnection(
                nodes = emptyList(),
                pageInfo = QlPageInfo(hasMore = false, nextCursor = null),
            )
        } else {
            val result = GraphQlEngine
                .diContainer(env)
                .accountService
                .accounts(
                    after = after?.toPosition(),
                    limit = limit.coerceIn(0, MAX_ACCOUNTS_LIMIT),
                )

            QlAccountsConnection(
                nodes = result.accounts.map {
                    it.urls.toGraphqlResponse(
                        accountId = it.accountId,
                        displayName = it.displayName,
                        summary = it.summary,
                    )
                },
                pageInfo = QlPageInfo(
                    hasMore = result.hasMore,
                    nextCursor = result.nextPosition?.let { AccountsCursor.of(it).encode() },
                ),
            )
        }

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder(connection).build(),
        )
    }

    override fun account(
        username: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAccount?>> {
        return GraphQlEngine
            .dataLoaders(env)
            .accountDataLoader
            .get(env)
            .load(username)
            .thenApply { account ->
                DataFetcherResult.Builder<QlAccount?>(
                    account?.let {
                        it.urls.toGraphqlResponse(
                            accountId = it.accountId,
                            displayName = it.displayName,
                            summary = it.summary,
                        )
                    },
                ).build()
            }
    }

    override fun notes(
        query: QlAccountNotesQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAccountNotesConnection>> {
        val cursor = query.cursor?.let { NotesCursor.decode(it) }

        val connection = if (query.cursor != null && cursor == null) {
            QlAccountNotesConnection(
                nodes = emptyList(),
                pageInfo = QlPageInfo(hasMore = false, nextCursor = null),
            )
        } else {
            val page = GraphQlEngine.diContainer(env).noteReader.noteIds(
                username = query.username,
                after = cursor?.toPosition(),
                limit = query.limit,
            )

            QlAccountNotesConnection(
                nodes = page.ids.map { QlAccountNote(id = it) },
                pageInfo = QlPageInfo(
                    hasMore = page.hasMore,
                    nextCursor = page.nextPosition?.let { NotesCursor.of(it).encode() },
                ),
            )
        }

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder(connection).build(),
        )
    }

    override fun timeline(
        query: QlTimelineQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAccountNotesConnection>> {
        val cursor = query.cursor?.let { NotesCursor.decode(it) }

        val connection = if (query.cursor != null && cursor == null) {
            QlAccountNotesConnection(
                nodes = emptyList(),
                pageInfo = QlPageInfo(hasMore = false, nextCursor = null),
            )
        } else {
            val page = GraphQlEngine.diContainer(env).noteReader.timelineNoteIds(
                after = cursor?.toPosition(),
                limit = query.limit,
            )

            QlAccountNotesConnection(
                nodes = page.ids.map { QlAccountNote(id = it) },
                pageInfo = QlPageInfo(
                    hasMore = page.hasMore,
                    nextCursor = page.nextPosition?.let { NotesCursor.of(it).encode() },
                ),
            )
        }

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder(connection).build(),
        )
    }

    /**
     * 名前を引き当ててから返す。引けない名前で空の一覧を返すと、無いアカウントが
     * フォロワー 0 人のアカウントとして見える
     */
    override fun followers(
        query: QlAccountFollowersQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAccountFollowersConnection?>> {
        val cursor = query.cursor?.let { FollowersCursor.decode(it) }

        return GraphQlEngine
            .dataLoaders(env)
            .accountDataLoader
            .get(env)
            .load(query.username)
            .thenApply { account ->
                val connection = when {
                    account == null -> null

                    // 読めないカーソルは続きが無い扱い。開き直せば先頭から取れる
                    query.cursor != null && cursor == null -> QlAccountFollowersConnection(
                        nodes = listOf(),
                        pageInfo = QlPageInfo(hasMore = false, nextCursor = null),
                    )

                    else -> {
                        val diContainer = GraphQlEngine.diContainer(env)
                        val page = diContainer.accountService.followers(
                            username = account.urls.username,
                            afterActorUrl = cursor?.afterActorUrl,
                            limit = query.limit.coerceIn(0, MAX_FOLLOWERS_LIMIT),
                        )

                        QlAccountFollowersConnection(
                            nodes = page.followers.map { it.toGraphqlResponse(diContainer.remoteActorIconUrls) },
                            pageInfo = QlPageInfo(
                                hasMore = page.hasMore,
                                nextCursor = page.nextActorUrl?.let { FollowersCursor(afterActorUrl = it).encode() },
                            ),
                        )
                    }
                }

                DataFetcherResult.Builder<QlAccountFollowersConnection?>(connection).build()
            }
    }

    override fun note(
        username: String,
        id: PublicNoteId,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAccountNote?>> {
        val note = GraphQlEngine.diContainer(env).noteReader.note(username = username, publicId = id)
        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder<QlAccountNote?>(note?.let { QlAccountNote(id = id) }).build(),
        )
    }

    private companion object {
        const val MAX_ACCOUNTS_LIMIT = 100

        const val MAX_FOLLOWERS_LIMIT = 100
    }
}

/**
 * 相手が名乗っていないものは null のまま返す。
 *
 * `acct` のドメインをアクター文書の URL から作るのは、相手が名乗るのは
 * ドメインより前だけのため。相手のサーバーが別のドメインの acct を
 * 名乗っている場合はそちらが正だが、確かめるには相手の WebFinger を
 * 引くことになるので、ここでは URL のホストで組み立てる
 */
private fun StoredFollower.toGraphqlResponse(iconUrls: RemoteActorIconUrls): QlAccountFollower {
    val host = runCatching { URI(actorUri).host }.getOrNull()

    return QlAccountFollower(
        url = profileUrl ?: actorUri,
        acct = if (preferredUsername != null && host != null) "@$preferredUsername@$host" else null,
        displayName = displayName,
        iconUrl = iconUrl?.let { iconUrls.icon(actorUri = actorUri, sourceUrl = it) },
    )
}
