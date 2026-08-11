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
 * GraphQL の実行。スキーマは `:shared:graphql` から読む。
 *
 * native-image の制約が 2 つある。リゾルバは [RuntimeWiring] に明示して結線すること、
 * フィールドの値は `Map` で返すこと。どちらもリフレクション経路に入ると、
 * JVM では動いて native バイナリでだけ壊れる。詳細は docs/architecture.md を参照。
 */
class GraphQlEngine private constructor(
    private val graphQl: GraphQL,
) {
    /**
     * 問い合わせを 1 つ実行する。データフェッチャーは同期なので、呼ぶ側が
     * `Dispatchers.IO` に載せること。
     *
     * @param call データフェッチャーから Cookie を読み書きするために渡す
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

        // 解決に失敗しても例外にはならず errors に入る
        return graphQl.execute(input).toSpecification().toJsonElement() as JsonObject
    }

    private fun Any?.orEmptyMap(): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return this as? Map<String, Any?> ?: emptyMap()
    }

    companion object {
        private val CALL_KEY = Any()

        /** @param wirings 分野ごとの結線。管理画面のものは `admin/AdminGraphQl.kt` */
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

        /** データフェッチャーから呼び出し元を引く。Cookie の読み書きに要る */
        fun DataFetchingEnvironment.applicationCall(): ApplicationCall =
            requireNotNull(graphQlContext.get<ApplicationCall>(CALL_KEY)) {
                "GraphQLContext に ApplicationCall が無い"
            }

        /** native バイナリではリソースを resource-config.json に書かないと同梱されない */
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

/** 分野ごとの結線。1 か所にまとめるとフィールドが増えるたびに同じファイルが伸びる */
fun interface GraphQlWiring {
    fun contribute(builder: RuntimeWiring.Builder)
}
