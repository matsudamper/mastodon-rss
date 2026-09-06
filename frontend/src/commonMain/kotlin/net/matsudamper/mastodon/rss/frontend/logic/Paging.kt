package net.matsudamper.mastodon.rss.frontend.logic

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Query
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.apolloStore
import com.apollographql.cache.normalized.doNotStore
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.watch

/**
 * 続きを足しても 1 つの流れで受け取れる一覧
 */
interface Paging<T> {
    fun watch(): Flow<T>

    /**
     * 続きを取って一覧に足す。一覧は watch から流れるので、返すのは失敗したかどうかだけ
     */
    suspend fun loadMore(cursor: String): PagingLoadMoreResult
}

sealed interface PagingLoadMoreResult {
    data object Success : PagingLoadMoreResult

    data class Failure(
        val message: String,
    ) : PagingLoadMoreResult
}

/**
 * 先頭のページを watch し、続きのページは先頭のページのキャッシュに合体させる。
 *
 * 続きのページは変数（cursor）が違う別の問い合わせで、そのままキャッシュに書いても
 * 先頭のページを見ている watch には流れない。取ってきたものを先頭のページの内容と
 * 繋いで書き直すと、続きを足した後も一覧全体が同じ watch から流れてくる。
 *
 * 書き戻す先は [firstPage] のキャッシュなので、cursor 以外の変数（件数など）が
 * watch と違うと別のキャッシュに書くことになり、黙って何も出なくなる。
 * 1 つの一覧につき 1 つ作って使い回す
 *
 * @param appendPage 先頭のページの内容に続きを足したものを返す。
 *   次の cursor は取ってきた側のものにする
 * @param toResult watch から流す形に直す
 */
internal class CachedPaging<D : Query.Data, T>(
    private val client: ApolloClient,
    private val firstPage: Query<D>,
    private val nextPage: (cursor: String) -> Query<D>,
    private val appendPage: (cached: D, fetched: D) -> D,
    private val toResult: (ApolloResponse<D>) -> T,
) : Paging<T> {
    override fun watch(): Flow<T> {
        return client
            .query(firstPage)
            .fetchPolicy(FetchPolicy.NetworkOnly)
            .watch()
            .map { response -> toResult(response) }
    }

    override suspend fun loadMore(cursor: String): PagingLoadMoreResult {
        val response = client
            .query(nextPage(cursor))
            .fetchPolicy(FetchPolicy.NetworkOnly)
            // 続きのページ単体をキャッシュに残しても誰も読まない。合体させたものだけを書く
            .doNotStore(true)
            .execute()

        if (response.exception != null || response.errors.orEmpty().isNotEmpty()) {
            return PagingLoadMoreResult.Failure(response.failureMessage())
        }
        val fetched = response.data ?: return PagingLoadMoreResult.Failure(response.failureMessage())

        val store = client.apolloStore
        val cachedResponse = store.readOperation(firstPage)
        val cached = cachedResponse.data
            ?: return PagingLoadMoreResult.Failure(cachedResponse.failureMessage())

        // publish の既定は false で、渡さないと watch している側に通知が行かない
        store.writeOperation(
            operation = firstPage,
            data = appendPage(cached, fetched),
            publish = true,
        )
        return PagingLoadMoreResult.Success
    }

    private fun ApolloResponse<*>.failureMessage(): String {
        return exception?.message
            ?: errors?.joinToString("\n") { it.message }?.takeIf { it.isNotEmpty() }
            ?: "ネットワークエラー"
    }
}
