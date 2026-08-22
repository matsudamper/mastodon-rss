package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.graphql.data.NotesCursor
import net.matsudamper.mastodon.rss.graphql.model.AccountResolver
import net.matsudamper.mastodon.rss.graphql.model.QlAccount
import net.matsudamper.mastodon.rss.graphql.model.QlNotesConnection
import net.matsudamper.mastodon.rss.graphql.model.QlPageInfo

class AccountResolverImpl : AccountResolver {
    override fun notes(
        account: QlAccount,
        cursor: String?,
        limit: Int,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlNotesConnection>> {
        val after = cursor?.let { NotesCursor.decode(it) }

        val connection = if (cursor != null && after == null) {
            QlNotesConnection(
                nodes = emptyList(),
                pageInfo = QlPageInfo(hasMore = false, nextCursor = null),
            )
        } else {
            val diContainer = GraphQlEngine.diContainer(env)
            val page = diContainer.noteService.notes(
                username = account.username,
                after = after?.toPosition(),
                limit = limit,
            )

            QlNotesConnection(
                nodes = page.notes.map { it.toPublicGraphqlResponse(domain = diContainer.domain) },
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
}
