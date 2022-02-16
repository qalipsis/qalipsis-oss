package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.steps.ErrorProcessingStep
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore

/**
 * Steps at the end of a DAG when the deployment is distributed.
 *
 * @author Eric Jessé
 */
internal class DistributedDagTransitionStep<I>(
    id: StepId,
    dagId: DirectedAcyclicGraphId,
    private val nextDagId: DirectedAcyclicGraphId,
    factoryCampaignManager: FactoryCampaignManager,
    private val localAssignmentStore: LocalAssignmentStore,
    private val contextForwarder: ContextForwarder
) : DagTransitionStep<I>(id, dagId, factoryCampaignManager), ErrorProcessingStep<I, I> {

    override suspend fun execute(context: StepContext<I, I>) {
        if (localAssignmentStore.isLocal(context.scenarioId, context.minionId, nextDagId)) {
            super<DagTransitionStep>.execute(context)
        } else {
            contextForwarder.forward(context, listOf(nextDagId))
        }
    }

    override suspend fun complete(completionContext: CompletionContext) {
        super<DagTransitionStep>.complete(completionContext)
        if (!localAssignmentStore.isLocal(completionContext.scenarioId, completionContext.minionId, nextDagId)) {
            contextForwarder.forward(completionContext, listOf(nextDagId))
        }
    }

}
