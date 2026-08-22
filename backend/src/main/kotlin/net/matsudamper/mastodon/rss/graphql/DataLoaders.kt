package net.matsudamper.mastodon.rss.graphql

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.dataloader.AccountDataLoaderDefine
import net.matsudamper.mastodon.rss.dataloader.DataLoaderDefine
import net.matsudamper.mastodon.rss.dataloader.FollowerCountDataLoaderDefine
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry

/**
 * リクエスト 1 つ分の DataLoader。作ると同時に registry へ登録する。
 *
 * リゾルバに渡すのは [DataLoaderProvider] で、実体は [DataFetchingEnvironment] から取る。
 * ここで作った実体を直接渡すと、いま実行しているリクエストの registry に
 * 載っていない DataLoader を使えてしまい、まとめる先が分かれる。
 */
class DataLoaders(
    diContainer: DiContainer,
    private val dataLoaderRegistryBuilder: DataLoaderRegistry.Builder,
) {
    val accountDataLoader by register { AccountDataLoaderDefine(diContainer.actorDirectory) }

    val followerCountDataLoader by register { FollowerCountDataLoaderDefine(diContainer.accountService) }

    private fun <K : Any, V : Any> register(initializer: () -> DataLoaderDefine<K, V>): DataLoaderRegister<K, V> {
        val define = initializer()
        dataLoaderRegistryBuilder.register(define.key, define.getDataLoader())

        return DataLoaderRegister(define.key)
    }

    class DataLoaderProvider<K : Any, V : Any>(
        private val dataLoaderName: String,
    ) {
        fun get(env: DataFetchingEnvironment): DataLoader<K, V> {
            return checkNotNull(env.dataLoaderRegistry.getDataLoader(dataLoaderName)) {
                "$dataLoaderName が registry に無い"
            }
        }
    }

    private class DataLoaderRegister<K : Any, V : Any>(
        private val key: String,
    ) : ReadOnlyProperty<Any, DataLoaderProvider<K, V>> {
        override fun getValue(
            thisRef: Any,
            property: KProperty<*>,
        ): DataLoaderProvider<K, V> {
            return DataLoaderProvider(key)
        }
    }
}
