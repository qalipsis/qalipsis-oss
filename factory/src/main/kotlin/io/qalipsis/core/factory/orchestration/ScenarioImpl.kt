/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.factory.orchestration

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.executionprofile.ExecutionProfile
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.retry.NoRetryPolicy
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.runtime.DirectedAcyclicGraph
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.api.runtime.ScenarioStartStopConfiguration
import io.qalipsis.api.steps.Step
import io.qalipsis.api.sync.Slot
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.steps.DagTransitionStep
import io.qalipsis.core.feedbacks.CampaignStartedForDagFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import kotlinx.coroutines.runBlocking
import org.slf4j.event.Level
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of a [Scenario].
 */
internal class ScenarioImpl(
    override val name: ScenarioName,
    override val description: String? = null,
    override val version: String,
    override val builtAt: Instant,
    override val executionProfile: ExecutionProfile,
    override val defaultRetryPolicy: RetryPolicy = NoRetryPolicy(),
    override val minionsCount: Int = 1,
    private val factoryChannel: FactoryChannel,
) : Scenario {

    private val steps = ConcurrentHashMap<StepName, Slot<Pair<Step<*, *>, DirectedAcyclicGraph>>>()

    private val internalDags = ConcurrentHashMap<DirectedAcyclicGraphName, DirectedAcyclicGraph>()

    override val dags: Collection<DirectedAcyclicGraph>
        get() = internalDags.values

    override operator fun contains(dagId: DirectedAcyclicGraphName): Boolean {
        return dagId in internalDags.keys
    }

    override operator fun get(dagId: DirectedAcyclicGraphName): DirectedAcyclicGraph? {
        return internalDags[dagId]
    }

    override fun createIfAbsent(
        dagId: DirectedAcyclicGraphName,
        dagSupplier: (DirectedAcyclicGraphName) -> DirectedAcyclicGraph
    ): DirectedAcyclicGraph {
        return internalDags.computeIfAbsent(dagId, dagSupplier)
    }

    /**
     * Adds a step to the scenario.
     */
    override suspend fun addStep(dag: DirectedAcyclicGraph, step: Step<*, *>) {
        steps.computeIfAbsent(step.name) { Slot() }.set(step to dag)
    }

    /**
     * Finds a step with the expected ID or suspend until it is created or a timeout of 10 seconds happens.
     */
    override suspend fun findStep(stepName: StepName): Pair<Step<*, *>, DirectedAcyclicGraph> {
        return steps.computeIfAbsent(stepName) { Slot() }.get()
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun start(configuration: ScenarioStartStopConfiguration) {
        val scenarioName = this.name
        try {
            startAllDags(configuration.campaignKey, scenarioName)
        } catch (e: Exception) {
            log.error(e) { "An error occurred while starting the scenario $scenarioName: ${e.message}" }
            stopAllDags(configuration.campaignKey, scenarioName)
        }
    }

    private suspend fun startAllDags(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName
    ) {
        internalDags.values.forEach { dag ->
            CampaignStartedForDagFeedback(
                scenarioName = scenarioName,
                dagId = dag.name,
                campaignKey = campaignKey,
                status = FeedbackStatus.IN_PROGRESS
            ).also {
                log.trace { "Sending feedback: $it" }
                factoryChannel.publishFeedback(it)
            }

            val step = dag.rootStep.get()
            try {
                startStepRecursively(
                    step, StepStartStopContext(
                        campaignKey = campaignKey,
                        scenarioName = scenarioName,
                        dagId = dag.name,
                        stepName = step.name
                    )
                )

                CampaignStartedForDagFeedback(
                    scenarioName = scenarioName,
                    dagId = dag.name,
                    campaignKey = campaignKey,
                    status = FeedbackStatus.COMPLETED
                ).also {
                    log.trace { "Sending feedback: $it" }
                    factoryChannel.publishFeedback(it)
                }
            } catch (e: Exception) {
                CampaignStartedForDagFeedback(
                    scenarioName = scenarioName,
                    dagId = dag.name,
                    campaignKey = campaignKey,
                    status = FeedbackStatus.FAILED,
                    errorMessage = "The start of the DAG ${dag.name} failed: ${e.message}"
                ).also {
                    log.trace { "Sending feedback: $it" }
                    factoryChannel.publishFeedback(it)
                }
                throw e
            }
        }
    }

    private suspend fun startStepRecursively(
        step: Step<*, *>,
        context: StepStartStopContext
    ) {
        step.start(context.copy(stepName = step.name))
        step.next.filterNot { it is DagTransitionStep<*> }.forEach { nextStep ->
            startStepRecursively(nextStep, context)
        }
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun stop(configuration: ScenarioStartStopConfiguration) {
        val scenarioName = this.name
        stopAllDags(configuration.campaignKey, scenarioName)
    }

    private suspend fun stopAllDags(campaignKey: CampaignKey, scenarioName: ScenarioName) {
        internalDags.values.map { it.name to it.rootStep.get() }.forEach { (dagName, step) ->
            stopStepRecursively(
                step, StepStartStopContext(
                    campaignKey = campaignKey,
                    scenarioName = scenarioName,
                    dagId = dagName,
                    stepName = step.name
                )
            )
        }
    }


    private suspend fun stopStepRecursively(step: Step<*, *>, context: StepStartStopContext) {
        tryAndLogOrNull(log) {
            step.stop(context.copy(stepName = step.name))
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
