package net.matsudamper.mastodon.rss.graphql

import kotlin.test.Test
import kotlin.test.assertNotNull
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.execution.ExecutionContextBuilder
import graphql.execution.ExecutionId
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.SimpleInstrumentation
import graphql.execution.instrumentation.SimpleInstrumentationContext
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import graphql.language.OperationDefinition
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLTypeReference

class GraphQlOpenTelemetryInstrumentationTest {
    @Test
    fun `ChainedInstrumentation で beginExecuteOperation を合成できる`() {
        val instrumentation =
            ChainedInstrumentation(
                object : SimpleInstrumentation() {
                    override fun beginExecuteOperation(
                        parameters: InstrumentationExecuteOperationParameters,
                        state: graphql.execution.instrumentation.InstrumentationState?,
                    ) = SimpleInstrumentationContext.noOp<ExecutionResult>()
                },
            )

        val executionContext = executionContext(operationName = "AdminSession")
        val state =
            instrumentation
                .createStateAsync(
                    InstrumentationCreateStateParameters(
                        executionContext.graphQLSchema,
                        executionContext.executionInput,
                    ),
                ).get()
        val context =
            instrumentation.beginExecuteOperation(
                InstrumentationExecuteOperationParameters(executionContext),
                state,
            )

        assertNotNull(context)
        context.onCompleted(ExecutionResult.newExecutionResult().build(), null)
    }

    private fun executionContext(operationName: String): graphql.execution.ExecutionContext {
        val operationDefinition =
            OperationDefinition
                .newOperationDefinition()
                .name(operationName)
                .operation(OperationDefinition.Operation.QUERY)
                .build()
        val executionInput =
            ExecutionInput
                .newExecutionInput()
                .query("query $operationName { __typename }")
                .operationName(operationName)
                .build()
        val queryType =
            GraphQLObjectType
                .newObject()
                .name("Query")
                .field(
                    GraphQLFieldDefinition
                        .newFieldDefinition()
                        .name("__typename")
                        .type(GraphQLTypeReference.typeRef("String"))
                        .build(),
                ).build()
        val schema = GraphQLSchema.newSchema().query(queryType).build()
        return ExecutionContextBuilder
            .newExecutionContextBuilder()
            .executionId(ExecutionId.generate())
            .graphQLSchema(schema)
            .operationDefinition(operationDefinition)
            .executionInput(executionInput)
            .build()
    }
}
