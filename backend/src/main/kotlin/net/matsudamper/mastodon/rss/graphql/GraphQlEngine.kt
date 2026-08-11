package net.matsudamper.mastodon.rss.graphql

import kotlinx.serialization.json.JsonObject
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.ktor.server.application.ApplicationCall

/**
 * リゾルバは [RuntimeWiring] に明示して結線し、フィールドの値は `Map` で返すこと。
 * リフレクション経路に入ると native バイナリでだけ壊れる。
 */
class GraphQlEngine private constructor(
    private val graphQl: GraphQL,
) {
    /** データフェッチャーは同期なので、呼ぶ側が `Dispatchers.IO` に載せること */
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

        return graphQl.execute(input).toSpecification().toJsonElement() as JsonObject
    }

    private fun Any?.orEmptyMap(): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return this as? Map<String, Any?> ?: emptyMap()
    }

    companion object {
        private val CALL_KEY = Any()

        fun create(wirings: List<GraphQlWiring>): GraphQlEngine {
            val registry = readSchema()

            val runtimeWiring =
                RuntimeWiring
                    .newRuntimeWiring()
                    .apply { wirings.forEach { it.contribute(this) } }
                    .build()

            val schema = SchemaGenerator().makeExecutableSchema(registry, runtimeWiring)
            return GraphQlEngine(GraphQL.newGraphQL(schema).build())
        }

        fun DataFetchingEnvironment.applicationCall(): ApplicationCall {
            return requireNotNull(graphQlContext.get<ApplicationCall>(CALL_KEY)) {
                "GraphQLContext に ApplicationCall が無い"
            }
        }

        /** ディレクトリの列挙は native バイナリで効かないので、読むものを並べる */
        private val SCHEMA_RESOURCES =
            listOf(
                "graphql/schema.graphqls",
                "graphql/admin_query.graphqls",
                "graphql/admin_mutation.graphqls",
            )

        private fun readSchema(): TypeDefinitionRegistry {
            val parser = SchemaParser()

            return SCHEMA_RESOURCES.fold(TypeDefinitionRegistry()) { registry, resource ->
                registry.merge(parser.parse(readResource(resource)))
            }
        }

        private fun readResource(resource: String): String {
            val stream =
                GraphQlEngine::class.java.classLoader.getResourceAsStream(resource)
                    ?: throw IllegalStateException(
                        "$resource が見つからない。" +
                            ":shared:graphql が classpath にあるか、native バイナリなら resource-config.json を確かめること",
                    )

            return stream.use { it.readBytes().decodeToString() }
        }
    }
}

fun interface GraphQlWiring {
    fun contribute(builder: RuntimeWiring.Builder)
}
