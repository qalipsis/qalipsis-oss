package io.qalipsis.core.factories.orchestration

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.context.StepId
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.DirectedAcyclicGraph
import io.qalipsis.api.orchestration.Scenario
import io.qalipsis.api.orchestration.feedbacks.FeedbackProducer
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus
import io.qalipsis.api.rampup.RampUpStrategy
import io.qalipsis.api.retry.NoRetryPolicy
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.Step
import io.qalipsis.api.sync.Slot
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.cross.feedbacks.CampaignStartedForDagFeedback
import kotlinx.coroutines.runBlocking
import org.slf4j.event.Level
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of a [Scenario].
 */
class ScenarioImpl(
    override val id: ScenarioId,
    override val rampUpStrategy: RampUpStrategy,
    override val defaultRetryPolicy: RetryPolicy = NoRetryPolicy(),
    override val minionsCount: Int = 1,
    private val feedbackProducer: FeedbackProducer
) : Scenario {

    private val steps = ConcurrentHashMap<StepId, Slot<Pair<Step<*, *>, DirectedAcyclicGraph>>>()

    private val internalDags = ConcurrentHashMap<DirectedAcyclicGraphId, DirectedAcyclicGraph>()

    override val dags: Collection<DirectedAcyclicGraph>
        get() = internalDags.values

    override operator fun contains(dagId: DirectedAcyclicGraphId): Boolean {
        return dagId in internalDags.keys
    }

    override operator fun get(dagId: DirectedAcyclicGraphId): DirectedAcyclicGraph? {
        return internalDags[dagId]
    }

    override fun createIfAbsent(
        dagId: DirectedAcyclicGraphId,
        dagSupplier: (DirectedAcyclicGraphId) -> DirectedAcyclicGraph
    ): DirectedAcyclicGraph {
        return internalDags.computeIfAbsent(dagId, dagSupplier)
    }

    /**
     * Adds a step to the scenario.
     */
    override suspend fun addStep(dag: DirectedAcyclicGraph, step: Step<*, *>) {
        steps.computeIfAbsent(step.id) { Slot() }.set(step to dag)
    }

    /**
     * Finds a step with the expected ID or suspend until it is created or a timeout of 10 seconds happens.
     */
    override suspend fun findStep(stepId: StepId): Pair<Step<*, *>, DirectedAcyclicGraph>? {
        return steps.computeIfAbsent(stepId) { Slot() }.get()
    }


    @LogInput(level = Level.DEBUG)
    override fun start(campaignId: CampaignId) {
        val scenarioId = this.id
        runBlocking {
            internalDags.values.forEach { dag ->
                val feedback = CampaignStartedForDagFeedback(
                    scenarioId = scenarioId,
                    dagId = dag.id,
                    campaignId = campaignId,
                    status = FeedbackStatus.IN_PROGRESS
                )
                log.trace { "Sending feedback: $feedback" }
                feedbackProducer.publish(feedback)

                val step = dag.rootStep.get()
                startStepRecursively(
                    step, StepStartStopContext(
                        campaignId = campaignId,
                        scenarioId = scenarioId,
                        dagId = dag.id,
                        stepId = step.id
                    )
                )

                val completionFeedback = CampaignStartedForDagFeedback(
                    scenarioId = scenarioId,
                    dagId = dag.id,
                    campaignId = campaignId,
                    status = FeedbackStatus.COMPLETED
                )
                log.trace { "Sending feedback: $completionFeedback" }
                feedbackProducer.publish(completionFeedback)
            }
        }
    }

    private suspend fun startStepRecursively(step: Step<*, *>, context: StepStartStopContext) {
        // The start is not surrounded by a try catch because they all have to start successfully to be able to start
        // a campaign.
        step.start(context)
        step.next.forEach {
            startStepRecursively(it, context)
        }
    }

    @LogInput(level = Level.DEBUG)
    override fun stop(campaignId: CampaignId) {
        val scenarioId = this.id
        runBlocking {
            internalDags.values.map { it.id to it.rootStep.get() }.forEach {
                stopStepRecursively(
                    it.second, StepStartStopContext(
                        campaignId = campaignId,
                        scenarioId = scenarioId,
                        dagId = it.first,
                        stepId = it.second.id
                    )
                )
            }
        }
    }


    private suspend fun stopStepRecursively(step: Step<*, *>, context: StepStartStopContext) {
        tryAndLogOrNull(log) {
            step.stop(context)
        }
        step.next.forEach {
            stopStepRecursively(it, context)
        }
    }

    override fun destroy() {
        runBlocking {
            internalDags.values.map { it.rootStep.get() }.forEach {
                destroyStepRecursively(it)
            }
        }
    }

    private suspend fun destroyStepRecursively(step: Step<*, *>) {
        tryAndLogOrNull(log) {
            step.destroy()
        }
        step.next.forEach {
            destroyStepRecursively(it)
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
