package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.graphql.model.AccountNoteResolver
import net.matsudamper.mastodon.rss.graphql.model.QlAccountNote
import net.matsudamper.mastodon.rss.graphql.model.QlNoteReaction
import net.matsudamper.mastodon.rss.note.NoteUrls
import net.matsudamper.mastodon.rss.note.StoredNote
import net.matsudamper.mastodon.rss.repository.NoteReactionCount

class AccountNoteResolverImpl : AccountNoteResolver {
    override fun url(
        accountNote: QlAccountNote,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<String>> {
        return loadNote(accountNote, env).thenApply { note ->
            val domain = GraphQlEngine.diContainer(env).domain
            DataFetcherResult.Builder(NoteUrls(domain = domain, publicId = note.publicId).noteUrl).build()
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

    override fun favouriteCount(
        accountNote: QlAccountNote,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<Int>> {
        return loadReactions(accountNote, env).thenApply { reactions ->
            val favourites = reactions.filter { it.emoji == FAVOURITE_EMOJI }.sumOf { it.count }
            DataFetcherResult.Builder(favourites).build()
        }
    }

    override fun reactions(
        accountNote: QlAccountNote,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<List<QlNoteReaction>>> {
        return loadReactions(accountNote, env).thenApply { reactions ->
            val stamps = reactions
                .filter { it.emoji != FAVOURITE_EMOJI }
                .map {
                    QlNoteReaction(
                        name = displayName(it.emoji),
                        imageUrl = it.emojiImageUrl,
                        count = it.count,
                    )
                }
            DataFetcherResult.Builder(stamps).build()
        }
    }

    /**
     * カスタム絵文字に付く `:` は、相手のサーバーが本文に埋め込むための記法で名前の一部ではない
     */
    private fun displayName(emoji: String): String = emoji.removeSurrounding(":")

    private fun loadReactions(
        accountNote: QlAccountNote,
        env: DataFetchingEnvironment,
    ): CompletionStage<List<NoteReactionCount>> {
        return GraphQlEngine
            .dataLoaders(env)
            .noteReactionsDataLoader
            .get(env)
            .load(accountNote.id)
            .thenApply { reactions -> reactions.orEmpty() }
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

    private companion object {
        /**
         * 絵文字を伴わないお気に入り。記録側と同じ綴り
         */
        const val FAVOURITE_EMOJI = ""
    }
}
