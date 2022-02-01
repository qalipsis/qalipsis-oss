package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.steps.ErrorProcessingStep
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
) : BlackHoleStep<I>(id), ErrorProcessingStep<I, Unit> {

    override suspend fun execute(context: StepContext<I, Unit>) {
        if (context.isTail) {
            factoryCampaignManager.notifyCompleteMinion(
                context.minionId,
                context.campaignId,
                context.scenarioId,
                dagId
            )
        }
        if (!context.isExhausted) {
            super<BlackHoleStep>.execute(context)
        }
    }
}
