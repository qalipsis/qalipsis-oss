package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepName
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager

/**
 * Steps at the end of a DAG with no next step.
 *
 * @author Eric Jessé
 */
internal class DeadEndStep<I>(
    id: StepName,
    private val dagId: DirectedAcyclicGraphName,
    private val factoryCampaignManager: FactoryCampaignManager
) : BlackHoleStep<I>(id) {

    override suspend fun complete(completionContext: CompletionContext) {
        factoryCampaignManager.notifyCompleteMinion(
            completionContext.minionId,
            completionContext.campaignName,
            completionContext.scenarioName,
            dagId
        )
    }
}
