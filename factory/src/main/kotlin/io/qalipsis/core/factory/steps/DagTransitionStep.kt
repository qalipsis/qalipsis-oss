package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.StepId
import io.qalipsis.api.steps.ErrorProcessingStep
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager

/**
 * Steps at the end of a DAG.
 *
 * @author Eric Jessé
 */
internal class DagTransitionStep<I>(
    id: StepId,
    private val dagId: DirectedAcyclicGraphId,
    private val factoryCampaignManager: FactoryCampaignManager
) : PipeStep<I>(id), ErrorProcessingStep<I, I> {

    override suspend fun complete(completionContext: CompletionContext) {
        factoryCampaignManager.notifyCompleteMinion(
            completionContext.minionId,
            completionContext.campaignId,
            completionContext.scenarioId,
            dagId
        )
    }
}
