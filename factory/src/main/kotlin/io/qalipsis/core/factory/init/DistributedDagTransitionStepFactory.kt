package io.qalipsis.core.factory.init

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.StepId
import io.qalipsis.api.steps.Step
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore
import io.qalipsis.core.factory.steps.ContextForwarder
import io.qalipsis.core.factory.steps.DeadEndStep
import io.qalipsis.core.factory.steps.DistributedDagTransitionStep
import jakarta.inject.Singleton

/**
 * Implementation of [DagTransitionStepFactory] for distributed deployments.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(notEnv = [ExecutionEnvironments.STANDALONE, ExecutionEnvironments.SINGLE_FACTORY])
internal class DistributedDagTransitionStepFactory(
    private val factoryCampaignManager: FactoryCampaignManager,
    private val localAssignmentStore: LocalAssignmentStore,
    private val contextForwarder: ContextForwarder
) : DagTransitionStepFactory {

    override fun createDeadEnd(stepId: StepId, sourceDagId: DirectedAcyclicGraphId): DeadEndStep<*> {
        return DeadEndStep<Any?>(stepId, sourceDagId, factoryCampaignManager)
    }

    override fun createTransition(
        stepId: StepId,
        sourceDagId: DirectedAcyclicGraphId,
        targetDagId: DirectedAcyclicGraphId
    ): Step<*, Any?> {
        return DistributedDagTransitionStep(
            stepId,
            sourceDagId,
            targetDagId,
            factoryCampaignManager,
            localAssignmentStore,
            contextForwarder
        )
    }
}