package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.StepContext
import io.qalipsis.core.factory.orchestration.TransportableContext

/**
 * Service to produce [TransportableContext] to a specified factory.
 *
 * @author Eric Jessé
 */
internal interface ContextForwarder {

    suspend fun forward(context: StepContext<*, *>, dags: Collection<DirectedAcyclicGraphId>)

    suspend fun forward(context: CompletionContext, dags: Collection<DirectedAcyclicGraphId>)

}
