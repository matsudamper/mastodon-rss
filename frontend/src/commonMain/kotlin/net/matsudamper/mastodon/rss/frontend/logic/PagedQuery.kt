package net.matsudamper.mastodon.rss.frontend.logic

import kotlinx.coroutines.flow.Flow
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Query
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.apolloStore
import com.apollographql.cache.normalized.doNotStore
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.watch

/**
 * 先頭のページを watch し、続きのページは先頭のページのキャッシュに合体させる。
 *
 * 続きのページは変数（cursor）が違う別の問い合わせで、そのままキャッシュに書いても
 * 先頭のページを見ている watch には流れない。取ってきたものを先頭のページの内容と
 * 繋いで書き直すと、続きを足した後も一覧全体が同じ watch から流れてくる。
 *
 * @param appendPage 先頭のページの内容に続きを足したものを返す。
 *   次の cursor は取ってきた側のものにする
 */
class PagedQuery<D : Query.Data>(
    private val client: ApolloClient,
    private val firstPage: Query<D>,
    private val nextPage: (cursor: String) -> Query<D>,
    private val appendPage: (cached: D, fetched: D) -> D,
) {
    fun watch(): Flow<ApolloResponse<D>> {
        return client
            .query(firstPage)
            .fetchPolicy(FetchPolicy.NetworkOnly)
            .watch()
    }

    /**
     * 続きを取って先頭のページに足す。一覧は watch から流れるので、返すのは失敗したかどうかだけ
     */
    suspend fun loadMore(cursor: String): PagedQueryLoadMoreResult {
        val response = client
            .query(nextPage(cursor))
            .fetchPolicy(FetchPolicy.NetworkOnly)
            // 続きのページ単体をキャッシュに残しても誰も読まない。合体させたものだけを書く
            .doNotStore(true)
            .execute()

        if (response.exception != null || response.errors.orEmpty().isNotEmpty()) {
            return PagedQueryLoadMoreResult.Failure(response.failureMessage())
        }
        val fetched = response.data ?: return PagedQueryLoadMoreResult.Failure(response.failureMessage())

        val store = client.apolloStore
        val cachedResponse = store.readOperation(firstPage)
        val cached = cachedResponse.data
            ?: return PagedQueryLoadMoreResult.Failure(cachedResponse.failureMessage())

        store.writeOperation(firstPage, appendPage(cached, fetched))
        return PagedQueryLoadMoreResult.Success
    }

    private fun ApolloResponse<*>.failureMessage(): String {
        return exception?.message
            ?: errors?.joinToString("\n") { it.message }?.takeIf { it.isNotEmpty() }
            ?: "ネットワークエラー"
    }
}

sealed interface PagedQueryLoadMoreResult {
    data object Success : PagedQueryLoadMoreResult

    data class Failure(
        val message: String,
    ) : PagedQueryLoadMoreResult
}
