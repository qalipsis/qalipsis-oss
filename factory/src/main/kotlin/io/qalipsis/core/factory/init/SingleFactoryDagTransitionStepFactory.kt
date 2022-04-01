package io.qalipsis.core.factory.init

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepName
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

    override fun createDeadEnd(stepName: StepName, sourceDagId: DirectedAcyclicGraphName): DeadEndStep<*> {
        return DeadEndStep<Any?>(stepName, sourceDagId, factoryCampaignManager)
    }

    override fun createTransition(
        stepName: StepName,
        sourceDagId: DirectedAcyclicGraphName,
        targetDagId: DirectedAcyclicGraphName
    ): Step<*, Any?> {
        return DagTransitionStep(
            stepName,
            sourceDagId,
            factoryCampaignManager
        )
    }
}