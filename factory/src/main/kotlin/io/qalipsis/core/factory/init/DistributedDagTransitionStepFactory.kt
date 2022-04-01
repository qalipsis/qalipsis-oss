package io.qalipsis.core.factory.init

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepName
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

    override fun createDeadEnd(stepName: StepName, sourceDagId: DirectedAcyclicGraphName): DeadEndStep<*> {
        return DeadEndStep<Any?>(stepName, sourceDagId, factoryCampaignManager)
    }

    override fun createTransition(
        stepName: StepName,
        sourceDagId: DirectedAcyclicGraphName,
        targetDagId: DirectedAcyclicGraphName
    ): Step<*, Any?> {
        return DistributedDagTransitionStep(
            stepName,
            sourceDagId,
            targetDagId,
            factoryCampaignManager,
            localAssignmentStore,
            contextForwarder
        )
    }
}