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
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.coroutines.contextualLaunch
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.runtime.Minion
import io.qalipsis.api.sync.Latch
import io.qalipsis.api.sync.SuspendedCountLatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.slf4j.MDC
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/**
 * <p>
 * A Minion is an actor entity, which simulates exactly one user, device or external system by running the steps of
 * the scenario that can be executed on the factory.
 * </p>
 *
 * <p>A Minion is identified by an ID, which is actually a trace ID in the sense of distributing tracing requirements.
 * Each step it performs opens and closes a new span. For technical reasons, a minion can run in parallel or
 * consecutively on different factories.
 * </p>
 */
internal open class MinionImpl(
    override val id: MinionId,
    override val campaignKey: CampaignKey,
    override val scenarioName: ScenarioName,
    pauseAtStart: Boolean = true,
    val isSingleton: Boolean = true,
    private val executingStepsGauge: AtomicInteger
) : Minion {

    /**
     * Latch to suspend caller while the minion is not started.
     */
    private val startLatch = Latch(true, "minionStartLatch-${id}")

    /**
     * Counter to assign an ID to the running jobs.
     */
    private val jobIndex = AtomicLong(Long.MIN_VALUE)

    /**
     * Map of running jobs attached to a unique identifier.
     */
    private val runningJobs = ConcurrentHashMap<Long, Job>()

    /**
     * Cancellation state of the minion.
     */
    private var cancelled = false

    /**
     * Actions to perform when the minion normally completes.
     */
    private val onCompleteHooks: MutableList<suspend (() -> Unit)> = mutableListOf()

    /**
     * Latch suspending until all the jobs are complete then triggering all the statements of [onCompleteHooks] successively.
     */
    private val runningJobsLatch = SuspendedCountLatch {
        completeMdcContext()
        if (!cancelled) {
            log.trace { "The minion is now complete, executing the hooks" }
            onCompleteHooks.forEach {
                tryAndLogOrNull(log) {
                    it()
                }
            }
        }
        cleanMdcContext()
    }

    /**
     * Computed count of active steps.
     */
    val stepsCount: Int
        get() = executingStepsGauge.get()

    init {
        if (!pauseAtStart) {
            doStart()
        }
    }

    override fun onComplete(block: suspend (() -> Unit)) {
        onCompleteHooks.add(block)
    }

    override suspend fun start() {
        doStart()
    }

    private fun doStart() {
        if (!isStarted()) {
            startLatch.cancel()
        }
    }

    override fun isStarted(): Boolean {
        return !startLatch.isLocked
    }

    suspend fun reset(paused: Boolean = false) {
        cancelled = false
        if (paused) {
            startLatch.lock()
        } else {
            startLatch.cancel()
        }
        jobIndex.set(Long.MIN_VALUE)
        runningJobs.clear()
        runningJobsLatch.reset()
        executingStepsGauge.set(0)
    }

    override suspend fun launch(
        scope: CoroutineScope,
        context: CoroutineContext,
        countLatch: SuspendedCountLatch?,
        block: suspend CoroutineScope.() -> Unit
    ): Job? {
        return if (cancelled) {
            log.trace { "Minion was cancelled, no new job can be started" }
            null
        } else {
            val jobId = jobIndex.getAndIncrement()

            executingStepsGauge.incrementAndGet()
            runningJobsLatch.increment()
            countLatch?.increment()

            MDC.put("job", "$jobId")
            log.trace {
                "Adding a job to minion (active jobs: ${runningJobs.keys.toList().joinToString(", ")})"
            }
            scope.contextualLaunch(context) {
                waitForStart()
                try {
                    log.trace { "Executing the minion job $jobId" }
                    this.block()
                    log.trace { "Successfully executed the minion job $jobId" }
                } catch (e: CancellationException) {
                    // Ignore the error.
                } catch (e: Exception) {
                    log.warn(e) { "An error occurred while executing the minion job $jobId: ${e.message}" }
                    throw e
                } finally {
                    executingStepsGauge.decrementAndGet()
                    countLatch?.decrement()

                    if (!cancelled) {
                        runningJobs.remove(jobId)
                        runningJobsLatch.decrement()
                        log.trace {
                            "Minion job $jobId was completed (active jobs: ${
                                runningJobs.keys.toList().joinToString(", ")
                            })"
                        }
                    }
                }
            }.also {
                runningJobs[jobId] = it
            }
        }
    }

    override suspend fun cancel() {
        log.trace { "Cancelling minion $id" }
        cancelled = true
        startLatch.cancel()
        for (job in runningJobs.values) {
            kotlin.runCatching {
                job.cancel()
            }
        }
        log.trace { "Cancellation of minions $id completed" }
        runningJobsLatch.cancel()
    }

    override suspend fun waitForStart() {
        if (!isStarted()) {
            log.trace { "Waiting for the minion to start" }
            startLatch.await()
            log.trace { "The minion was just started, now going on" }
        }
    }

    override suspend fun join() {
        completeMdcContext()
        log.trace { "Joining minion $id" }
        waitForStart()
        runningJobsLatch.awaitActivity()
        runningJobsLatch.await()
        log.trace { "Minion $id completed" }
        cleanMdcContext()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MinionImpl) return false

        if (id != other.id) return false
        if (campaignKey != other.campaignKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + campaignKey.hashCode()
        return result
    }


    companion object {

        private val log = logger()

    }
}
