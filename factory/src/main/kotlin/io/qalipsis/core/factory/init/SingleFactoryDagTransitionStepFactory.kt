package io.qalipsis.core.factory.init

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.StepId
import io.qalipsis.api.steps.Step
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.steps.DagTransitionStep
import io.qalipsis.core.factory.steps.DeadEndStep
import jakarta.inject.Singleton

/**
 * Implementation of [DagTransitionStepFactory] for deployments having a single factory.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.STANDALONE, ExecutionEnvironments.SINGLE_FACTORY])
internal class SingleFactoryDagTransitionStepFactory(
    private val factoryCampaignManager: FactoryCampaignManager
) : DagTransitionStepFactory {

    override fun createDeadEnd(stepId: StepId, sourceDagId: DirectedAcyclicGraphId): DeadEndStep<*> {
        return DeadEndStep<Any?>(stepId, sourceDagId, factoryCampaignManager)
    }

    override fun createTransition(
        stepId: StepId,
        sourceDagId: DirectedAcyclicGraphId,
        targetDagId: DirectedAcyclicGraphId
    ): Step<*, Any?> {
        return DagTransitionStep(
            stepId,
            sourceDagId,
            factoryCampaignManager
        )
    }
}