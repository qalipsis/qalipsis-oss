package io.evolue.api.steps.datasource

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.steps.AbstractStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

/**
 * Parent step for data sources.
 * The step is responsible for detecting when it is over. The context will be marked as completed in that case.
 *
 * @author Eric Jessé
 */
abstract class FlowDatasourceStep<O>(
        id: StepId,
        retryPolicy: RetryPolicy?,
        private val dataSupplier: suspend () -> Flow<O>
) : AbstractStep<Unit, O>(id, retryPolicy) {

    override suspend fun execute(context: StepContext<Unit, O>) {
        dataSupplier().collect { value -> context.output.send(value) }
        context.isCompleted = true
    }
}