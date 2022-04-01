package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.steps.ErrorProcessingStep
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore

/**
 * Steps at the end of a DAG when the deployment is distributed.
 *
 * @author Eric Jessé
 */
internal class DistributedDagTransitionStep<I>(
    id: StepName,
    dagId: DirectedAcyclicGraphName,
    private val nextDagId: DirectedAcyclicGraphName,
    factoryCampaignManager: FactoryCampaignManager,
    private val localAssignmentStore: LocalAssignmentStore,
    private val contextForwarder: ContextForwarder
) : DagTransitionStep<I>(id, dagId, factoryCampaignManager), ErrorProcessingStep<I, I> {

    override suspend fun execute(context: StepContext<I, I>) {
        if (localAssignmentStore.isLocal(context.scenarioName, context.minionId, nextDagId)) {
            super<DagTransitionStep>.execute(context)
        } else {
            contextForwarder.forward(context, listOf(nextDagId))
        }
    }

    override suspend fun complete(completionContext: CompletionContext) {
        super<DagTransitionStep>.complete(completionContext)
        if (!localAssignmentStore.isLocal(completionContext.scenarioName, completionContext.minionId, nextDagId)) {
            contextForwarder.forward(completionContext, listOf(nextDagId))
        }
    }

}
