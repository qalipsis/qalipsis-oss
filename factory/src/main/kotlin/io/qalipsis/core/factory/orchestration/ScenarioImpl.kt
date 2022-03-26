package io.qalipsis.core.factory.orchestration

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.context.StepId
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.rampup.RampUpStrategy
import io.qalipsis.api.retry.NoRetryPolicy
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.runtime.DirectedAcyclicGraph
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.api.steps.Step
import io.qalipsis.api.sync.Slot
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.feedbacks.CampaignStartedForDagFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.slf4j.event.Level
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of a [Scenario].
 */
internal class ScenarioImpl(
    override val id: ScenarioId,
    override val rampUpStrategy: RampUpStrategy,
    override val defaultRetryPolicy: RetryPolicy = NoRetryPolicy(),
    override val minionsCount: Int = 1,
    private val factoryChannel: FactoryChannel,
    private val stepStartTimeout: Duration = Duration.ofSeconds(30)
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
    override suspend fun findStep(stepId: StepId): Pair<Step<*, *>, DirectedAcyclicGraph> {
        return steps.computeIfAbsent(stepId) { Slot() }.get()
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun start(campaignId: CampaignId) {
        val scenarioId = this.id
        try {
            startAllDags(scenarioId, campaignId)
        } catch (e: Exception) {
            log.error(e) { "An error occurred while starting the scenario $scenarioId: ${e.message}" }
            stopAllDags(campaignId, scenarioId)
        }
    }

    private suspend fun startAllDags(
        scenarioId: ScenarioId,
        campaignId: CampaignId
    ) {
        internalDags.values.forEach { dag ->
            CampaignStartedForDagFeedback(
                scenarioId = scenarioId,
                dagId = dag.id,
                campaignId = campaignId,
                status = FeedbackStatus.IN_PROGRESS
            ).also {
                log.trace { "Sending feedback: $it" }
                factoryChannel.publishFeedback(it)
            }

            val step = dag.rootStep.get()
            try {
                startStepRecursively(
                    step, StepStartStopContext(
                        campaignId = campaignId,
                        scenarioId = scenarioId,
                        dagId = dag.id,
                        stepId = step.id
                    )
                )

                CampaignStartedForDagFeedback(
                    scenarioId = scenarioId,
                    dagId = dag.id,
                    campaignId = campaignId,
                    status = FeedbackStatus.COMPLETED
                ).also {
                    log.trace { "Sending feedback: $it" }
                    factoryChannel.publishFeedback(it)
                }
            } catch (e: Exception) {
                CampaignStartedForDagFeedback(
                    scenarioId = scenarioId,
                    dagId = dag.id,
                    campaignId = campaignId,
                    status = FeedbackStatus.FAILED,
                    error = "The start of the DAG ${dag.id} failed: ${e.message}"
                ).also {
                    log.trace { "Sending feedback: $it" }
                    factoryChannel.publishFeedback(it)
                }
                throw e
            }
        }
    }

    private suspend fun startStepRecursively(step: Step<*, *>, context: StepStartStopContext) {
        // The start is not surrounded by a try catch because they all have to start successfully to be able to start
        // a campaign.
        withTimeout(stepStartTimeout.toMillis()) {
            step.start(context)
        }
        step.next.forEach {
            startStepRecursively(it, context)
        }
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun stop(campaignId: CampaignId) {
        val scenarioId = this.id
        stopAllDags(campaignId, scenarioId)
    }

    private suspend fun stopAllDags(campaignId: CampaignId, scenarioId: ScenarioId) {
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
