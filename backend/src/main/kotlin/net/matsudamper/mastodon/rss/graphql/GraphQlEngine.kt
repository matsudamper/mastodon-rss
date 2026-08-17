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
import net.matsudamper.mastodon.rss.graphql.data.GraphQlRequest
import org.dataloader.DataLoaderRegistry

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
        // DataLoader はリクエストごとに作る。使い回すと、前のリクエストで引いた結果が
        // そのまま返る。アカウントを追加しても消えるまで見えない、という形になる
        val dataLoaderRegistryBuilder = DataLoaderRegistry.Builder()
        val dataLoaders = DataLoaders(
            diContainer = diContainer,
            dataLoaderRegistryBuilder = dataLoaderRegistryBuilder,
        )

        val input = ExecutionInput
            .newExecutionInput(request.query)
            .operationName(request.operationName)
            .variables(variablesOf(request))
            .dataLoaderRegistry(dataLoaderRegistryBuilder.build())
            .graphQLContext(mapOf(CONTEXT_KEY to createContext(call)))
            .graphQLContext(mapOf(DI_CONTAINER_KEY to diContainer))
            .graphQLContext(mapOf(DATA_LOADERS_KEY to dataLoaders))
            .build()

        return withContext(Dispatchers.IO) {
            GraphQlValues.toJsonElement(
                graphQl.execute(input).toSpecification(),
            ) as JsonObject
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
        private val DATA_LOADERS_KEY = Any()

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

        fun dataLoaders(env: DataFetchingEnvironment): DataLoaders {
            return requireNotNull(env.graphQlContext.get(DATA_LOADERS_KEY)) {
                "GraphQLContext に DataLoaders が無い"
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
