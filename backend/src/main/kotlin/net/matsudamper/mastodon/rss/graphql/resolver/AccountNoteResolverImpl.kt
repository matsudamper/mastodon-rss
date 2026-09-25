package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletionStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.graphql.model.AccountNoteResolver
import net.matsudamper.mastodon.rss.graphql.model.QlAccount
import net.matsudamper.mastodon.rss.graphql.model.QlAccountNote
import net.matsudamper.mastodon.rss.graphql.model.QlLinkPreview
import net.matsudamper.mastodon.rss.graphql.model.QlNoteStamp
import net.matsudamper.mastodon.rss.note.NoteUrls
import net.matsudamper.mastodon.rss.note.StoredNote
import net.matsudamper.mastodon.rss.telemetry.withOpenTelemetryContext

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
        return GraphQlEngine
            .dataLoaders(env)
            .noteFavouriteCountDataLoader
            .get(env)
            .load(accountNote.id)
            .thenApply { count -> DataFetcherResult.Builder(count ?: 0).build() }
    }

    override fun stamps(
        accountNote: QlAccountNote,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<List<QlNoteStamp>>> {
        return GraphQlEngine
            .dataLoaders(env)
            .noteStampsDataLoader
            .get(env)
            .load(accountNote.id)
            .thenApply { stamps ->
                val qlStamps = stamps.orEmpty().map {
                    QlNoteStamp(
                        // カスタム絵文字に付く `:` は、相手のサーバーが本文に埋め込むための記法で名前の一部ではない
                        name = it.emoji.removeSurrounding(":"),
                        imageUrl = it.emojiImageUrl,
                        count = it.count,
                    )
                }
                DataFetcherResult.Builder(qlStamps).build()
            }
    }

    override fun account(
        accountNote: QlAccountNote,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAccount>> {
        return GraphQlEngine
            .dataLoaders(env)
            .accountByNoteDataLoader
            .get(env)
            .load(accountNote.id)
            .thenApply { account ->
                checkNotNull(account) { "投稿したアカウントが見つからない: ${accountNote.id}" }
                DataFetcherResult.Builder(
                    account.urls.toGraphqlResponse(
                        accountId = account.accountId,
                        displayName = account.displayName,
                        summary = account.summary,
                    ),
                ).build()
            }
    }

    override fun linkUrls(
        accountNote: QlAccountNote,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<List<String>>> {
        val linkPreviewService = GraphQlEngine.diContainer(env).linkPreviewService
        return loadNote(accountNote, env).thenApply { note ->
            DataFetcherResult.Builder(linkPreviewService.links(note.contentHtml)).build()
        }
    }

    override fun linkPreviews(
        accountNote: QlAccountNote,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<List<QlLinkPreview>>> {
        val diContainer = GraphQlEngine.diContainer(env)
        val linkPreviewService = diContainer.linkPreviewService
        val imageUrls = diContainer.linkPreviewImageUrls
        return loadNote(accountNote, env).thenCompose { note ->
            CoroutineScope(Dispatchers.IO.withOpenTelemetryContext()).future {
                val previews = linkPreviewService.previews(note.contentHtml).mapIndexed { linkIndex, preview ->
                    QlLinkPreview(
                        url = preview.url,
                        title = preview.title,
                        siteName = preview.siteName,
                        imageUrl = preview.imageUrl?.let { sourceUrl ->
                            imageUrls.image(
                                notePublicId = note.publicId.value,
                                linkIndex = linkIndex,
                                sourceUrl = sourceUrl,
                            )
                        },
                    )
                }
                DataFetcherResult.Builder(previews).build()
            }
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
