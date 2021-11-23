package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Step to convert a context to a collection of them.
 *
 * It is typically used when a collection is output from a step but each record of the collection
 * has to be individually processed by the next steps.
 *
 * @author Eric Jessé
 */
internal class FlatMapStep<I, O>(
    id: StepId,
    retryPolicy: RetryPolicy?,
    @Suppress("UNCHECKED_CAST") private val block: ((input: I) -> Flow<O>) = { input ->
        when (input) {
            null -> emptyFlow()
            is Collection<*> ->
                input.asFlow() as Flow<O>

            is Array<*> ->
                input.asFlow() as Flow<O>

                is Sequence<*> ->
                    input.asFlow() as Flow<O>

                is Map<*, *> ->
                    input.entries.map { e -> e.key to e.value }.asFlow() as Flow<O>

                else ->
                    flowOf(input) as Flow<O>
            }
        }
) : AbstractStep<I, O>(id, retryPolicy) {

    override suspend fun execute(context: StepContext<I, O>) {
        val input = context.receive()
        block(input).collect { context.send(it) }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
