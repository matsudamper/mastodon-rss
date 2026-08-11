package net.matsudamper.mastodon.rss.graphql

import kotlinx.serialization.json.JsonObject
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.kickstart.tools.GraphQLResolver
import graphql.kickstart.tools.SchemaParser
import graphql.schema.DataFetchingEnvironment
import io.ktor.server.application.ApplicationCall

/**
 * 結線は graphql-java-tools (kickstart) がリフレクションで行う。native-image 向けの
 * 登録は `graalvm/GraphQlReflectionFeature` がまとめて行う。
 */
class GraphQlEngine private constructor(
    private val graphQl: GraphQL,
) {
    /** リゾルバは同期に済ませてあるので、呼ぶ側が `Dispatchers.IO` に載せること */
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

        fun create(resolvers: List<GraphQLResolver<*>>): GraphQlEngine {
            val schema =
                SchemaParser
                    .newParser()
                    .schemaString(readSchema())
                    .resolvers(resolvers)
                    .build()
                    .makeExecutableSchema()

            return GraphQlEngine(GraphQL.newGraphQL(schema).build())
        }

        /** Cookie を読み書きするリゾルバはここから [ApplicationCall] を取る */
        fun DataFetchingEnvironment.applicationCall(): ApplicationCall {
            return requireNotNull(graphQlContext.get<ApplicationCall>(CALL_KEY)) {
                "GraphQLContext に ApplicationCall が無い"
            }
        }

        /** 読むファイルは一覧から引く。ディレクトリの列挙は native バイナリで効かない */
        private fun readSchema(): String {
            val fileNames =
                readResource(SCHEMA_LIST_RESOURCE)
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toList()

            check(fileNames.isNotEmpty()) { "$SCHEMA_LIST_RESOURCE が空。スキーマが 1 つも無い" }

            return fileNames.joinToString(separator = "\n") { readResource("$SCHEMA_DIRECTORY/$it") }
        }

        private const val SCHEMA_DIRECTORY = "graphql"
        private const val SCHEMA_LIST_RESOURCE = "$SCHEMA_DIRECTORY/schema-list.txt"

        private fun readResource(resource: String): String {
            val stream =
                GraphQlEngine::class.java.classLoader.getResourceAsStream(resource)
                    ?: throw IllegalStateException(
                        "$resource が見つからない。" +
                            ":backend:graphql が classpath にあるか、native バイナリなら resource-config.json を確かめること",
                    )

            return stream.use { it.readBytes().decodeToString() }
        }
    }
}
