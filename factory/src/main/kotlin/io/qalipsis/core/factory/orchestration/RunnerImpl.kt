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

import io.micrometer.core.instrument.Counter
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.runtime.DirectedAcyclicGraph
import io.qalipsis.api.runtime.Minion
import io.qalipsis.api.steps.ErrorProcessingStep
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepDecorator
import io.qalipsis.api.steps.StepExecutor
import io.qalipsis.api.sync.Latch
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.campaign.CampaignLifeCycleAware
import io.qalipsis.core.factory.context.StepContextBuilder
import io.qalipsis.core.factory.context.StepContextImpl
import io.qalipsis.core.factory.context.TailStepContext
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import org.slf4j.MDC
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The Runner is the masterpiece of the factory and drives the minions to execute directed acyclic graphs (aka DAGs)
 * as requested by the head.
 *
 * It creates the Minions and assign them a list of DAGs, which are not directly connected altogether.
 *
 * The DAGs generally have input and output which are connected via messaging to the other
 * DAGs of the same minions running on different factories.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class RunnerImpl(
    @Named(Executors.CAMPAIGN_EXECUTOR_NAME) private val coroutineScope: CoroutineScope
) : StepExecutor, Runner, CampaignLifeCycleAware {

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private val runningStepsGauges = ConcurrentHashMap<ScenarioName, AtomicInteger>()

    private val executedStepCounters = ConcurrentHashMap<ScenarioName, Counter>()

    override suspend fun close(campaign: Campaign) {
        runningStepsGauges.clear()
        executedStepCounters.clear()
    }

    @LogInput
    override suspend fun run(minion: Minion, dag: DirectedAcyclicGraph) {
        minion.waitForStart()

        val step = dag.rootStep.get()
        val stepContext = StepContextImpl<Unit, Any>(
            input = Channel<Unit>(1).also { it.send(Unit) },
            campaignKey = minion.campaignKey,
            minionId = minion.id,
            scenarioName = dag.scenario.name,
            stepName = step.name
        )

        @Suppress("UNCHECKED_CAST")
        runMinion(minion, step, stepContext)
    }

    @LogInput
    override suspend fun runMinion(
        minion: Minion, rootStep: Step<*, *>, stepContext: StepContext<*, *>,
        completionConsumer: (suspend (stepContext: StepContext<*, *>) -> Unit)?
    ) {
        minion.completeMdcContext()
        MDC.put("step", rootStep.name)
        minion.start()

        log.trace { "Running minion" }
        try {
            minion.launch(coroutineScope) {
                log.trace { "Starting the execution of a subtree of tasks for the minion" }
                propagateStepExecution(this, minion, rootStep, stepContext, completionConsumer)
                log.trace { "Execution of the subtree of tasks is completed for the minion" }
            }
        } finally {
            MDC.clear()
        }
    }

    @LogInput
    override suspend fun execute(
        minion: Minion, rootStep: Step<*, *>, stepContext: StepContext<*, *>,
        completionConsumer: (suspend (stepContext: StepContext<*, *>) -> Unit)?
    ): Job? {
        minion.completeMdcContext()
        return try {
            MDC.put("step", rootStep.name)
            minion.launch(coroutineScope) {
                propagateStepExecution(this, minion, rootStep, stepContext, completionConsumer)
            }
        } finally {
            MDC.clear()
        }
    }

    override suspend fun complete(minion: Minion, rootStep: Step<*, *>, completionContext: CompletionContext): Job? {
        minion.completeMdcContext()
        MDC.put("step", rootStep.name)
        return try {
            minion.launch(coroutineScope) {
                propagatePrematureCompletion(this, completionContext, listOf(rootStep), minion)
            }
        } finally {
            MDC.clear()
        }
    }

    /**
     * Propagate the completion of the minion execution to the [nextSteps] and their successors.
     */
    private suspend fun propagatePrematureCompletion(
        minionScope: CoroutineScope,
        completionContext: CompletionContext,
        nextSteps: Collection<Step<*, *>>,
        minion: Minion
    ) {
        minion.completeMdcContext()
        nextSteps.forEach { step ->
            log.trace { "Propagating the minion's completion on step ${step.name} with context $completionContext" }
            MDC.put("step", step.name)
            minion.launch(minionScope) {
                tryAndLogOrNull(log) {
                    step.complete(completionContext)
                }
                propagatePrematureCompletion(minionScope, completionContext, step.next, minion)
            }
        }
        minion.cleanMdcContext()
    }

    /**
     * Executes a step and its successors.
     *
     * @param minionScope the [CoroutineScope] to use to execute the [Job] related to the minion
     * @param minion the minion owning the execution workflow
     * @param step step to execute prior to its successors
     * @param stepContext [StepContext] to apply to the execution of [step]
     * @param completionConsumer action to execute after the lately executed step of the tree
     */
    private suspend fun propagateStepExecution(
        minionScope: CoroutineScope,
        minion: Minion,
        step: Step<*, *>,
        stepContext: StepContext<*, *>,
        completionConsumer: (suspend (stepContext: StepContext<*, *>) -> Unit)?
    ) {
        log.trace { "Executing step ${step.name} with context $stepContext" }
        var latchForExternalCompletion: Latch? = null
        if (step.next.isEmpty() && completionConsumer != null) {
            // If the step is the latest one of the chain and a completion operation is provided, the output channel
            // is provided to completion operation.
            log.trace { "Launching the completionConsumer for the latest step of the graph ${step.name}" }
            latchForExternalCompletion = Latch(true, "step-execution-propagation")
            MDC.put("step", "_completion")
            minion.launch(minionScope) {
                latchForExternalCompletion.await() // Blocks the execution of the completion block until the step is really executed.
                completionConsumer(stepContext)
            }
        } else if (step.next.isNotEmpty()) {
            // The output of the context is consumed asynchronously to release the current coroutine and let the step execute.
            minion.launch(minionScope) {
                consumeOutputAndExecuteNextSteps(minionScope, minion, step.next, stepContext, completionConsumer)
            }
        }

        executeSingleStep(minion, step, stepContext)

        // Once the execution is done - successfully or not - and the tail is received, we notify the step
        // with the completion of the minion workflow at its level.
        if (stepContext.isTail) {
            log.trace { "Propagating the completion of the step ${step.name} for minion ${stepContext.minionId} from context $stepContext" }
            step.complete(stepContext.equivalentCompletionContext)
        }
        latchForExternalCompletion?.release()
    }

    /**
     * Consumes the records from the output of [stepContext] and starts the [Job]s to execute the successor steps
     * for each record.
     *
     * @param minionScope the [CoroutineScope] to use to execute the [Job] related to the minion
     * @param minion the minion owning the execution workflow
     * @param nextSteps successors to make consume the output of [stepContext] and execute each of its records
     * @param stepContext [StepContext] to apply to the execution of [step]
     * @param completionConsumer action to execute after the lately executed step of the tree
     */
    private suspend fun consumeOutputAndExecuteNextSteps(
        minionScope: CoroutineScope,
        minion: Minion,
        nextSteps: Collection<Step<*, *>>,
        stepContext: StepContext<*, *>,
        completionConsumer: (suspend (stepContext: StepContext<*, *>) -> Unit)?
    ) {
        log.trace { "Starting the coroutine to consume output and execute next steps of step ${stepContext.stepName} with context $stepContext" }

        var propagatedTail = false
        (stepContext as? StepContextImpl<*, *>)?.consume { outputRecord ->
            // Mark that the tail is already propagated to the next steps.
            propagatedTail = propagatedTail || outputRecord.isTail
            nextSteps.forEach { nextStep ->
                // Each next step is executed in its individual coroutine and with a dedicated context.
                @Suppress("UNCHECKED_CAST")
                val nextContext = StepContextBuilder.next<Any?, Any?, Any?>(
                    outputRecord.value,
                    stepContext as StepContext<Any?, Any?>,
                    nextStep.name
                )
                nextContext.isTail = outputRecord.isTail
                MDC.put("step", nextStep.name)
                minion.launch(minionScope) {
                    log.trace { "Launching the coroutine for the next step ${nextStep.name}" }
                    propagateStepExecution(minionScope, minion, nextStep, nextContext, completionConsumer)
                }
            }
        }

        if (stepContext.isExhausted) {
            log.trace { "The context is exhausted" }
            nextSteps.forEach { nextStep ->
                val nextContext = StepContextBuilder.next(stepContext, nextStep.name)
                MDC.put("step", nextStep.name)
                minion.launch(minionScope) {
                    log.trace { "Launching the coroutine for the next error processing step ${nextStep.name}" }
                    propagateStepExecution(minionScope, minion, nextStep, nextContext, completionConsumer)
                }
            }
        } else if (!stepContext.generatedOutput) {
            log.trace { "The context $stepContext generated no output" }
            // If the execution workflow is interrupted because of lack of data, the optional completion consumer
            // is executed.
            if (completionConsumer != null) {
                log.trace { "Executing the completion consumer" }
                MDC.put("step", "_completion")
                minion.launch(minionScope) {
                    completionConsumer(stepContext)
                }
            }
        }

        // Propagate the dummy tail to ensure that the completion of the minion is performed.
        if (stepContext.isTail && !stepContext.isExhausted && !propagatedTail && nextSteps.isNotEmpty()) {
            (stepContext as? StepContextImpl<*, *>)?.run {
                // We have to ensure that the jobs to complete that step and its descendants are executed after
                // the ones that consume all the generated outputs, in order to avoid race concurrency.
                log.trace { "Waiting for all the generated records during the iteration to be generated" }
                awaitEmptyOutput()
            }

            log.trace { "Propagating completion for minion ${stepContext.minionId} from context $stepContext" }
            val tailContext = TailStepContext(stepContext)
            nextSteps.forEach { nextStep ->
                MDC.put("step", nextStep.name)
                minion.launch(minionScope) {
                    log.trace { "Launching the coroutine for the next step ${nextStep.name}" }
                    propagateStepExecution(
                        minionScope,
                        minion,
                        nextStep,
                        tailContext.copy(stepName = nextStep.name),
                        completionConsumer
                    )
                }
            }
        }
    }

    /**
     * Executes a single step and monitors its execution.
     */
    private suspend fun executeSingleStep(
        minion: Minion,
        step: Step<*, *>,
        stepContext: StepContext<*, *>
    ) {
        if (stepContext !is TailStepContext) {
            if (!stepContext.isExhausted || isErrorProcessingStep(step)) {
                log.trace { "Performing the execution of the step..." }
                try {
                    executeStep(minion, step as Step<Any?, Any?>, stepContext as StepContext<Any?, Any?>)
                } catch (t: Throwable) {
                    stepContext.addError(StepError(t))
                    stepContext.isExhausted = true
                    if (log.isTraceEnabled) {
                        log.warn(t) { "Step completed with an exception, the context is marked as exhausted: ${t.message}" }
                    } else {
                        log.warn { "Step completed with an exception, the context is marked as exhausted: ${t.message}" }
                    }
                }
            } else {
                log.trace { "The step will not be executed on minion because the context is exhausted and the step cannot process it" }
            }
        }

        // Once the step was executed, the context can be closed since it is no longer used.
        stepContext.close()
    }


    private fun isErrorProcessingStep(step: Step<*, *>): Boolean {
        return if (step is StepDecorator<*, *>) {
            isErrorProcessingStep(step.decorated)
        } else {
            step is ErrorProcessingStep
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
