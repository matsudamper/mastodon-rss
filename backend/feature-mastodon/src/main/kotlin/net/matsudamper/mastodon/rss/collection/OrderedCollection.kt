package net.matsudamper.mastodon.rss.collection

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.matsudamper.mastodon.rss.activitypub.StringListSerializer

/**
 * 順序のある集合。中身は [OrderedCollectionPage] に分けて返す
 */
@Serializable
data class OrderedCollection(
    @SerialName("@context")
    @Serializable(with = StringListSerializer::class)
    val context: List<String> = DEFAULT_CONTEXT,
    val id: String,
    val type: String = TYPE,
    val totalItems: Long,
    /**
     * 最初のページの URL。中身が無くても返す
     */
    val first: String,
) {
    companion object {
        const val TYPE: String = "OrderedCollection"

        val DEFAULT_CONTEXT: List<String> = listOf("https://www.w3.org/ns/activitystreams")
    }
}

/**
 * [OrderedCollection] の 1 ページ。
 *
 * @param next 次のページの URL。最後のページなら null にして、キーごと出さない
 */
@Serializable
data class OrderedCollectionPage<T>(
    @SerialName("@context")
    @Serializable(with = StringListSerializer::class)
    val context: List<String> = OrderedCollection.DEFAULT_CONTEXT,
    val id: String,
    val type: String = TYPE,
    val totalItems: Long,
    val partOf: String,
    val orderedItems: List<T>,
    val next: String? = null,
) {
    companion object {
        const val TYPE: String = "OrderedCollectionPage"
    }
}

const val COLLECTION_PAGE_SIZE: Int = 12

/**
 * ページを指すクエリパラメータの名前。
 *
 * 値は直前のページの最後の 1 件を指す cursor。空文字なら先頭のページ
 */
const val COLLECTION_CURSOR_PARAM: String = "cursor"
