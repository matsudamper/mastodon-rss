package net.matsudamper.mastodon.rss.graphql

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import graphql.schema.DataFetchingEnvironment
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.Context
import net.matsudamper.mastodon.rss.dataloader.AccountDataLoaderDefine
import net.matsudamper.mastodon.rss.dataloader.AccountNoteDataLoaderDefine
import net.matsudamper.mastodon.rss.dataloader.DataLoaderDefine
import net.matsudamper.mastodon.rss.dataloader.FollowerCountDataLoaderDefine
import net.matsudamper.mastodon.rss.dataloader.OtelBatchLoaderScheduler
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
    private val otelContext: Context,
    private val openTelemetry: OpenTelemetry?,
) {
    val accountDataLoader by register { AccountDataLoaderDefine(diContainer.accountService) }

    val followerCountDataLoader by register { FollowerCountDataLoaderDefine(diContainer.accountService) }

    val accountNoteDataLoader by register { AccountNoteDataLoaderDefine(diContainer.noteStore) }

    private fun <K : Any, V : Any> register(initializer: () -> DataLoaderDefine<K, V>): DataLoaderRegister<K, V> {
        val define = initializer()
        val dataLoader = define.getDataLoader()
        if (openTelemetry != null) {
            val scheduler = OtelBatchLoaderScheduler(openTelemetry, otelContext, "DataLoader.${define.key}")
            val otelOptions =
                dataLoader.options.transform {
                    it.setBatchLoaderScheduler(scheduler)
                }
            dataLoaderRegistryBuilder.register(define.key, dataLoader.transform { it.options(otelOptions) })
        } else {
            dataLoaderRegistryBuilder.register(define.key, dataLoader)
        }

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
