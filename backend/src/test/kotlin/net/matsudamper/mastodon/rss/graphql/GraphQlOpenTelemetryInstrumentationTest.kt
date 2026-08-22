package net.matsudamper.mastodon.rss.graphql

import kotlin.test.Test
import kotlin.test.assertNotNull
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.execution.ExecutionContextBuilder
import graphql.execution.ExecutionId
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.SimpleInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import graphql.language.OperationDefinition
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLTypeReference
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.graphql.v20_0.GraphQLTelemetry

class GraphQlOpenTelemetryInstrumentationTest {
    @Test
    fun `state が null でも beginExecuteOperation が落ちない`() {
        val openTelemetry = OpenTelemetry.noop()
        val telemetry = GraphQLTelemetry.builder(openTelemetry).build()
        val instrumentation = GraphQlOpenTelemetryInstrumentation(telemetry.createInstrumentation())

        val context =
            instrumentation.beginExecuteOperation(
                parameters = parameters(operationName = "AdminSession"),
                state = null,
            )

        assertNotNull(context)
        context.onCompleted(ExecutionResult.newExecutionResult().build(), null)
    }

    @Test
    fun `delegate が null を返しても beginExecuteOperation が落ちない`() {
        val instrumentation =
            GraphQlOpenTelemetryInstrumentation(
                object : SimpleInstrumentation() {
                    override fun beginExecuteOperation(
                        parameters: InstrumentationExecuteOperationParameters,
                        state: graphql.execution.instrumentation.InstrumentationState?,
                    ): InstrumentationContext<ExecutionResult>? = null
                },
            )

        val context =
            instrumentation.beginExecuteOperation(
                parameters = parameters(operationName = "AdminSession"),
                state = null,
            )

        assertNotNull(context)
        context.onCompleted(ExecutionResult.newExecutionResult().build(), null)
    }

    private fun parameters(operationName: String): InstrumentationExecuteOperationParameters {
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
        val executionContext =
            ExecutionContextBuilder
                .newExecutionContextBuilder()
                .executionId(ExecutionId.generate())
                .graphQLSchema(schema)
                .operationDefinition(operationDefinition)
                .executionInput(executionInput)
                .build()
        return InstrumentationExecuteOperationParameters(executionContext)
    }
}
