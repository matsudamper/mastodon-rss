package net.matsudamper.mastodon.rss.graphql

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.kickstart.tools.GraphQLResolver
import graphql.kickstart.tools.SchemaParser
import graphql.schema.DataFetchingEnvironment
import io.ktor.server.application.ApplicationCall
import net.matsudamper.mastodon.rss.GraphqlExceptions
import net.matsudamper.mastodon.rss.graphql.data.GraphQlRequest
import org.slf4j.LoggerFactory

/**
 * 結線は graphql-java-tools (kickstart) がリフレクションで行う。native-image 向けの
 * 登録は `graalvm/GraphQlReflectionFeature` がまとめて行う。
 */
class GraphQlEngine private constructor(
    private val graphQl: GraphQL,
    private val createContext: (ApplicationCall) -> GraphQlContext,
    private val diContainer: DiContainer,
) {
    suspend fun execute(
        request: GraphQlRequest,
        call: ApplicationCall,
    ): JsonObject {
        val input = ExecutionInput
            .newExecutionInput(request.query)
            .operationName(request.operationName)
            .variables(variablesOf(request))
            .graphQLContext(mapOf(CONTEXT_KEY to createContext(call)))
            .graphQLContext(mapOf(DI_CONTAINER_KEY to diContainer))
            .build()

        return withContext(Dispatchers.IO) {
            val executionResult = graphQl.execute(input)
            val response = GraphQlValues.toJsonElement(executionResult.toSpecification()) as JsonObject
            if (executionResult.errors.isEmpty()) return@withContext response

            executionResult.errors.forEach { error ->
                val dataFetchingException = (error as? ExceptionWhileDataFetching)?.exception
                when {
                    // 構文エラーや検証エラーなど、DataFetcher に到達する前にクライアントの入力で弾かれたもの。
                    // 未認証のクライアントが不正なクエリを送るだけで ERROR ログを埋められてしまうため記録しない
                    error !is ExceptionWhileDataFetching -> {
                        logger.debug("GraphQL の実行に失敗した(クライアント起因): {}", error.message)
                    }

                    // 未ログインなど、想定内の業務エラー
                    dataFetchingException is GraphqlExceptions -> {
                        logger.debug("GraphQL の実行に失敗した(既知のエラー): {}", error.message)
                    }

                    else -> {
                        logger.error(
                            "GraphQL の実行に失敗した: {}",
                            error.message,
                            dataFetchingException,
                        )
                    }
                }
            }
            val genericErrors =
                JsonArray(
                    listOf(JsonObject(mapOf("message" to JsonPrimitive(GENERIC_ERROR_MESSAGE)))),
                )
            JsonObject(
                response + ("errors" to genericErrors),
            )
        }
    }

    private fun variablesOf(request: GraphQlRequest): Map<String, Any?> {
        val raw = request.variables?.let { GraphQlValues.toRawValue(it) }

        @Suppress("UNCHECKED_CAST")
        return raw as? Map<String, Any?> ?: emptyMap()
    }

    companion object {
        private val CONTEXT_KEY = Any()
        private val DI_CONTAINER_KEY = Any()
        private val logger = LoggerFactory.getLogger(GraphQlEngine::class.java)

        internal const val GENERIC_ERROR_MESSAGE = "GraphQL の実行に失敗した"

        fun create(
            resolvers: List<GraphQLResolver<*>>,
            createContext: (ApplicationCall) -> GraphQlContext,
            diContainer: DiContainer,
        ): GraphQlEngine {
            val schema = SchemaParser
                .newParser()
                .schemaString(readSchema())
                .scalars(UnixTimeScalar.value)
                .resolvers(resolvers)
                .build()
                .makeExecutableSchema()

            return GraphQlEngine(
                graphQl = GraphQL.newGraphQL(schema).build(),
                createContext = createContext,
                diContainer = diContainer,
            )
        }

        fun graphQlContext(env: DataFetchingEnvironment): GraphQlContext {
            return requireNotNull(env.graphQlContext.get(CONTEXT_KEY)) {
                "GraphQLContext に GraphQlContext が無い"
            }
        }

        fun diContainer(env: DataFetchingEnvironment): DiContainer {
            return requireNotNull(env.graphQlContext.get(DI_CONTAINER_KEY)) {
                "GraphQLContext に DiContainer が無い"
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
