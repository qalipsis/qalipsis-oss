package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.StepId
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager

/**
 * Steps at the end of a DAG with no next step.
 *
 * @author Eric Jessé
 */
internal class DeadEndStep<I>(
    id: StepId,
    private val dagId: DirectedAcyclicGraphId,
    private val factoryCampaignManager: FactoryCampaignManager
) : BlackHoleStep<I>(id) {

    override suspend fun complete(completionContext: CompletionContext) {
        factoryCampaignManager.notifyCompleteMinion(
            completionContext.minionId,
            completionContext.campaignId,
            completionContext.scenarioId,
            dagId
        )
    }
}
