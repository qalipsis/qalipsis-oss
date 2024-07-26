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

package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.runtime.Minion
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepDecorator
import io.qalipsis.api.steps.StepExecutor
import io.qalipsis.api.sync.SuspendedCountLatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decorator of all steps to improve and secure normal behavior in a highly
 * concurrent context.
 *
 * @author Eric Jessé
 */
internal class WorkflowStepDecorator<I, O>(
    override val decorated: Step<I, O>,
    private val reportLiveStateRegistry: CampaignReportLiveStateRegistry,
    private val stepStartTimeout: Duration = Duration.ofSeconds(30)
) : Step<I, O>, StepExecutor, StepDecorator<I, O> {

    override val name: StepName
        get() = decorated.name

    override var retryPolicy: RetryPolicy? = decorated.retryPolicy

    override val next = decorated.next

    private var started = AtomicBoolean()

    private val minionsInProgress = ConcurrentHashMap<MinionId, SuspendedCountLatch>()

    private val debugEnteredMinions = supplyIf(log.isTraceEnabled) { concurrentSet<String>() }

    private val debugExitedMinions = supplyIf(log.isTraceEnabled) { concurrentSet<String>() }

    override suspend fun start(context: StepStartStopContext) {
        if (started.compareAndExchange(false, true)) {
            if (log.isTraceEnabled) {
                reportLiveStateRegistry.put(
                    context.campaignKey,
                    context.scenarioName,
                    context.stepName,
                    ReportMessageSeverity.INFO,
                    "DAG: ${context.dagId}"
                )
            }
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(stepStartTimeout.toMillis()) {
                    decorated.start(context)
                }
            }
        }
    }

    override suspend fun stop(context: StepStartStopContext) {
        if (started.compareAndExchange(true, false)) {
            if (log.isTraceEnabled) {
                val minionsStillInProgress = debugEnteredMinions!! - debugExitedMinions!!
                reportLiveStateRegistry.put(
                    context.campaignKey,
                    context.scenarioName,
                    context.stepName,
                    ReportMessageSeverity.INFO,
                    "Minions still in progress ${minionsStillInProgress.size}: $minionsStillInProgress"
                )
            }
            minionsInProgress.clear()
            super<StepDecorator>.stop(context)
        }
    }

    override suspend fun execute(minion: Minion, context: StepContext<I, O>) {
        debugEnteredMinions?.add(minion.id)
        val counter = minionsInProgress.computeIfAbsent(minion.id) { minionId ->
            SuspendedCountLatch { minionsInProgress.remove(minionId) }
        }
        counter.increment()
        try {
            super<StepDecorator>.execute(minion, context)
        } catch (e: CancellationException) {
            throw e.cause ?: e
        } finally {
            debugExitedMinions?.add(minion.id)
            val runningWorkflows = counter.decrement()
            log.trace { "$runningWorkflows workflows are still active for the minion" }
        }
    }

    override suspend fun execute(context: StepContext<I, O>) {
        throw NotImplementedError()
    }

    override suspend fun complete(completionContext: CompletionContext) {
        if (debugEnteredMinions != null && debugExitedMinions != null) {
            if (completionContext.minionId !in debugEnteredMinions) {
                log.warn { "The minion ${completionContext.minionId} is being completed but never visited the step ${decorated.name}" }
            } else if (completionContext.minionId !in debugExitedMinions) {
                log.warn {
                    "The minion ${completionContext.minionId} is still visiting the step ${decorated.name} with ${
                        minionsInProgress[completionContext.minionId]?.get() ?: 0
                    } active workflows"
                }
            }
        }
        // Wait that all the workflows executing the step for the same minion are completed.
        val counter = minionsInProgress[completionContext.minionId]
        log.trace { "Waiting for all the ${counter?.get() ?: 0} workflow(s) of minion to be completed" }
        counter?.await()
        log.trace { "Completing the step ${decorated.name} for the context $completionContext" }
        super<StepDecorator>.complete(completionContext)
    }

    companion object {
        private val log = logger()
    }

}
