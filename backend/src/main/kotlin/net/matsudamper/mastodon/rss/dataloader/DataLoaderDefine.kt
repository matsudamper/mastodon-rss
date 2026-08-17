package net.matsudamper.mastodon.rss.dataloader

import org.dataloader.DataLoader

/**
 * DataLoader 1 つ分の定義。
 *
 * 作るところと使うところを分けるための型。実際の登録と取り出しは
 * [net.matsudamper.mastodon.rss.graphql.DataLoaders] が持つ。
 */
interface DataLoaderDefine<Key : Any, R : Any> {
    /**
     * 登録名。リクエストごとの registry から引くときのキーになる
     */
    val key: String

    fun getDataLoader(): DataLoader<Key, R>
}
