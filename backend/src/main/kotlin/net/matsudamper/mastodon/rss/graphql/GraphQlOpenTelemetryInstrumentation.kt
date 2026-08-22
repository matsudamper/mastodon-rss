package net.matsudamper.mastodon.rss.graphql

import graphql.ExecutionResult
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimpleInstrumentationContext
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.graphql.common.v12_0.internal.OpenTelemetryInstrumentationState

internal class GraphQlOpenTelemetryInstrumentation(
    private val delegate: Instrumentation,
) : Instrumentation by delegate {
    override fun beginExecuteOperation(
        parameters: InstrumentationExecuteOperationParameters,
        state: InstrumentationState,
    ): InstrumentationContext<ExecutionResult> {
        val context = delegate.beginExecuteOperation(parameters, state)
        val otelState = InstrumentationState.ofState(state) as? OpenTelemetryInstrumentationState
        val operationName =
            parameters.executionContext.operationDefinition.name
                ?: parameters.executionContext.executionInput.operationName
        if (otelState != null && !operationName.isNullOrBlank()) {
            Span.fromContext(otelState.context).updateName(operationName)
        }
        return context ?: SimpleInstrumentationContext.noOp()
    }
}
