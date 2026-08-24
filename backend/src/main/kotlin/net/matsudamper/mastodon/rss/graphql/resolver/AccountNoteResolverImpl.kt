package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.graphql.model.AccountNoteResolver
import net.matsudamper.mastodon.rss.graphql.model.QlAccountNote
import net.matsudamper.mastodon.rss.note.NoteUrls
import net.matsudamper.mastodon.rss.note.StoredNote

class AccountNoteResolverImpl : AccountNoteResolver {
    override fun url(
        accountNote: QlAccountNote,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<String>> {
        return loadNote(accountNote, env).thenApply { note ->
            val domain = GraphQlEngine.diContainer(env).domain
            DataFetcherResult.Builder(NoteUrls(domain = domain, publicId = note.publicId).noteId).build()
        }
    }

    override fun contentHtml(
        accountNote: QlAccountNote,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<String>> {
        return loadNote(accountNote, env).thenApply { note ->
            DataFetcherResult.Builder(note.contentHtml).build()
        }
    }

    override fun publishedAt(
        accountNote: QlAccountNote,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<Long>> {
        return loadNote(accountNote, env).thenApply { note ->
            DataFetcherResult.Builder(note.publishedAt.epochSecond).build()
        }
    }

    private fun loadNote(
        accountNote: QlAccountNote,
        env: DataFetchingEnvironment,
    ): CompletionStage<StoredNote> {
        return GraphQlEngine
            .dataLoaders(env)
            .accountNoteDataLoader
            .get(env)
            .load(accountNote.id)
            .thenApply { note ->
                checkNotNull(note) { "投稿が見つからない: ${accountNote.id}" }
            }
    }
}
