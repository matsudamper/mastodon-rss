package net.matsudamper.mastodon.rss.graphql

import java.util.concurrent.CompletableFuture
import graphql.ExecutionResult
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimpleInstrumentationContext
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.graphql.common.v12_0.internal.OpenTelemetryInstrumentationState

internal class GraphQlOpenTelemetryInstrumentation(
    private val delegate: Instrumentation,
) : Instrumentation by delegate {
    override fun createState(parameters: InstrumentationCreateStateParameters): InstrumentationState? =
        delegate.createState(parameters)

    override fun createStateAsync(
        parameters: InstrumentationCreateStateParameters,
    ): CompletableFuture<InstrumentationState> =
        requireNotNull(delegate.createStateAsync(parameters))

    override fun beginExecuteOperation(
        parameters: InstrumentationExecuteOperationParameters,
        state: InstrumentationState?,
    ): InstrumentationContext<ExecutionResult>? {
        val context =
            if (state == null) {
                SimpleInstrumentationContext.noOp()
            } else {
                delegate.beginExecuteOperation(parameters, state)
            }
        val operationName =
            parameters.executionContext.operationDefinition.name
                ?: parameters.executionContext.executionInput.operationName
        if (!operationName.isNullOrBlank()) {
            val otelState = state?.let { InstrumentationState.ofState(it) as? OpenTelemetryInstrumentationState }
            val span =
                when {
                    otelState != null -> Span.fromContext(otelState.context)
                    else -> Span.current().takeIf { it.spanContext.isValid }
                }
            span?.updateName(operationName)
        }
        return context ?: SimpleInstrumentationContext.noOp()
    }
}
