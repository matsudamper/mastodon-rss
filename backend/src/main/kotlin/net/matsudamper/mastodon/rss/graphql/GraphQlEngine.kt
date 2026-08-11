package net.matsudamper.mastodon.rss.graphql

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import io.ktor.server.application.ApplicationCall
import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.shared.GraphQlSchema

/**
 * GraphQL の実行。
 *
 * スキーマは `:shared:graphql` の `graphql/schema.graphqls` を読む。スキーマ優先で、
 * 型はここには書かない。同じファイルを `:frontend` の Apollo もコード生成の入力にする。
 *
 * リゾルバは [RuntimeWiring] に [graphql.schema.DataFetcher] を明示して結線する。
 * リフレクションで結線する仕組み（graphql-java-tools など）は native-image で動かない。
 * 同じ理由で、フィールドの値は `Map` で返して `PropertyDataFetcher` の
 * リフレクション経路に入れないこと。データクラスを返すと、JVM では動いて native
 * バイナリでだけ全フィールドが null になる、という形の不具合になる。
 *
 * @param graphQl 組み立て済みのスキーマ。作るのは [create]
 */
class GraphQlEngine private constructor(
    private val graphQl: GraphQL,
) {
    /**
     * 問い合わせを 1 つ実行して、返す JSON を組み立てる。
     *
     * 中で動く [graphql.schema.DataFetcher] は同期。パスワードの照合のように
     * 時間のかかるものが混ざるので、呼ぶ側が `Dispatchers.IO` に載せること。
     *
     * @param call データフェッチャーから Cookie を読み書きするために渡す。
     *   ログインはこれが無いとセッションを返せない
     */
    fun execute(
        request: GraphQlRequest,
        call: ApplicationCall,
    ): JsonObject {
        val input =
            ExecutionInput
                .newExecutionInput(request.query)
                .operationName(request.operationName)
                .variables(request.variables?.toRawValue().orEmptyMap())
                .graphQLContext(mapOf(CALL_KEY to call))
                .build()

        // toSpecification() が GraphQL の仕様どおりの { data, errors, extensions } を返す。
        // フィールドの解決に失敗しても例外にはならず errors に入るので、そのまま JSON にする
        return graphQl.execute(input).toSpecification().toJsonElement() as JsonObject
    }

    private fun Any?.orEmptyMap(): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return this as? Map<String, Any?> ?: emptyMap()
    }

    companion object {
        /**
         * 呼び出し元の [ApplicationCall] を [graphql.GraphQLContext] に入れるときの鍵。
         *
         * 文字列にすると綴りを間違えても気付けないので、この 1 つのオブジェクトを使う。
         */
        private val CALL_KEY = Any()

        /**
         * スキーマを読んで結線する。
         *
         * @param wirings 分野ごとの結線。管理画面のものは
         *   `admin/AdminGraphQl.kt`。増えるときはここに並べる
         */
        fun create(wirings: List<GraphQlWiring>): GraphQlEngine {
            val registry = SchemaParser().parse(readSchema())

            val runtimeWiring =
                RuntimeWiring
                    .newRuntimeWiring()
                    .apply { wirings.forEach { it.contribute(this) } }
                    .build()

            val schema = SchemaGenerator().makeExecutableSchema(registry, runtimeWiring)
            return GraphQlEngine(GraphQL.newGraphQL(schema).build())
        }

        /**
         * データフェッチャーから呼び出し元を引く。
         *
         * Cookie の読み書きに要る。GraphQL は HTTP を知らないので、
         * 実行の入口で入れたものをここで取り出す形になる。
         */
        fun DataFetchingEnvironment.applicationCall(): ApplicationCall =
            // 入れているのは execute() の 1 か所だけ。無いなら実行の入口を通っていない
            requireNotNull(graphQlContext.get<ApplicationCall>(CALL_KEY)) {
                "GraphQLContext に ApplicationCall が無い"
            }

        /**
         * スキーマの中身を読む。
         *
         * native バイナリではリソースを明示しないと同梱されない。登録先は
         * `src/main/resources/META-INF/native-image/.../resource-config.json`。
         * 抜けると起動した瞬間にここで落ちる。
         */
        private fun readSchema(): String {
            val stream =
                GraphQlEngine::class.java.classLoader.getResourceAsStream(GraphQlSchema.RESOURCE_PATH)
                    ?: throw IllegalStateException(
                        "${GraphQlSchema.RESOURCE_PATH} が見つからない。" +
                            ":shared:graphql が classpath にあるか、native バイナリなら resource-config.json を確かめること",
                    )

            return stream.use { it.readBytes().decodeToString() }
        }
    }
}

/**
 * 分野ごとの結線。
 *
 * スキーマ全体を 1 か所で結線すると、フィールドが増えるたびに同じファイルが伸びる。
 * `Query.admin` の下は管理画面の側が知っていればよい。
 */
fun interface GraphQlWiring {
    fun contribute(builder: RuntimeWiring.Builder)
}
