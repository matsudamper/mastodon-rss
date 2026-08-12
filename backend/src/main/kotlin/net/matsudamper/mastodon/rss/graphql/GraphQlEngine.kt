package net.matsudamper.mastodon.rss.graphql

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val createContext: (ApplicationCall) -> GraphQlContext,
) {
    suspend fun execute(
        request: GraphQlRequest,
        call: ApplicationCall,
    ): JsonObject {
        val input = ExecutionInput
            .newExecutionInput(request.query)
            .operationName(request.operationName)
            .variables(request.variables?.toRawValue().orEmptyMap())
            .graphQLContext(mapOf(CONTEXT_KEY to createContext(call)))
            .build()

        return withContext(Dispatchers.IO) {
            graphQl.execute(input)
                .toSpecification()
                .toJsonElement() as JsonObject
        }
    }

    private fun Any?.orEmptyMap(): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return this as? Map<String, Any?> ?: emptyMap()
    }

    companion object {
        private val CONTEXT_KEY = Any()

        fun create(
            resolvers: List<GraphQLResolver<*>>,
            createContext: (ApplicationCall) -> GraphQlContext,
        ): GraphQlEngine {
            val schema =
                SchemaParser
                    .newParser()
                    .schemaString(readSchema())
                    .resolvers(resolvers)
                    .build()
                    .makeExecutableSchema()

            return GraphQlEngine(
                graphQl = GraphQL.newGraphQL(schema).build(),
                createContext = createContext,
            )
        }

        fun DataFetchingEnvironment.graphQlContext(): GraphQlContext {
            return requireNotNull(graphQlContext.get<GraphQlContext>(CONTEXT_KEY)) {
                "GraphQLContext に GraphQlContext が無い"
            }
        }

        /**
         *  読むファイルは一覧から引く。ディレクトリの列挙は native バイナリで効かない
         */
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
