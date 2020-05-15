package io.evolue.core.factory.orchestration

import com.google.common.annotations.VisibleForTesting
import io.evolue.api.context.MinionId
import io.evolue.api.events.EventLogger
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.orchestration.Minion
import io.evolue.api.sync.SuspendedCountLatch
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

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
    val id: MinionId,
    pauseAtStart: Boolean = true,
    private val eventLogger: EventLogger,
    meterRegistry: MeterRegistry,
    private val maintenancePeriod: Duration = MAINTENANCE_PERIOD
) : Minion {

    /**
     * Latch to suspend caller while the minion is not started.
     */
    private val startLatch = SuspendedCountLatch(1)

    /**
     * Latch to suspend caller before the first step was attached.
     */
    private val executingLatch = SuspendedCountLatch(1)

    /**
     * List of the [step][io.evolue.api.steps.Step] jobs to be executed for the current minion.
     *
     */
    private val stepJobs = mutableListOf<Job>()

    /**
     * List of internal jobs waiting for completion of step jobs.
     */
    private val completionJobs = mutableListOf<Job>()

    /**
     * Cancellation state of the minion.
     */
    private var cancelled = false

    /**
     * Actions to perform when the minion normally completes.
     */
    private val onCompleteHooks: MutableList<suspend (() -> Unit)> = mutableListOf()

    /**
     * Latch suspending until all the jobs are complete.
     */
    private val jobsCompletion = SuspendedCountLatch(0) {
        onCompleteHooks.forEach { it() }
        eventLogger.debug("minion-completed", tags = mapOf("minion" to id))
    }

    private val logger = logger()

    /**
     * Mutex to sync operations on the jobs.
     */
    private val jobManagementMutex = Mutex()

    private val executingStepsGauge =
        meterRegistry.gauge("minion-executing-steps", listOf(Tag.of("minion", id)), AtomicInteger(0))

    private val maintenancePeriodTimer = meterRegistry.timer("minion-maintenance", "minion", id)

    /**
     * Computed count of active steps.
     */
    val stepsCount: Int
        get() = executingStepsGauge.get()

    init {
        eventLogger.debug("minion-created", tags = mapOf("minion" to id))
        if (!pauseAtStart) {
            runBlocking {
                start()
            }
        }

        // Start maintenance tasks.
        GlobalScope.launch {
            eventLogger.debug("minion-maintenance-routine-started", tags = mapOf("minion" to id))
            delay(maintenancePeriod.toMillis())
            while (!cancelled) {
                eventLogger.trace("minion-maintenance-operation-started", tags = mapOf("minion" to id))
                logger.trace("Running maintenance operations...")
                val start = System.nanoTime()
                jobManagementMutex.withLock {
                    stepJobs.removeIf { !it.isActive }
                    completionJobs.removeIf { !it.isActive }
                }
                Duration.ofNanos(System.nanoTime() - start).let {
                    maintenancePeriodTimer.record(it)
                    eventLogger.trace("minion-maintenance-operation-completed", it, tags = mapOf("minion" to id))
                    logger.trace("Maintenance operations executed in $it")
                }
                delay(maintenancePeriod.toMillis())
            }
            eventLogger.debug("minion-maintenance-routine-stopped", tags = mapOf("minion" to id))
        }
    }

    override fun onComplete(block: suspend (() -> Unit)) {
        onCompleteHooks.add(block)
    }

    /**
     * Releases the start flag to resume the callers waiting for the minion to start.
     */
    suspend fun start() {
        startLatch.release()
        eventLogger.debug("minion-started", tags = mapOf("minion" to id))
    }

    /**
     * Returns {@code true} of the start flag was released.
     */
    @VisibleForTesting
    fun isStarted(): Boolean {
        return !startLatch.isSuspended()
    }

    /**
     * Attaches a coroutine to the corresponding minion in order to monitor the activity state.
     * When the job is completed, the counter is decreased and the completion flag is closed to release the
     * [join][MinionImpl.join] function.
     */
    suspend fun attach(job: Job) {
        if (cancelled) {
            logger.trace("Minion $id was cancelled, received job is cancelled")
            job.cancel()
        } else {
            waitForStart()

            // Inventory a new running job.
            jobManagementMutex.withLock {
                jobsCompletion.increment()
                executingStepsGauge.incrementAndGet()
                job.invokeOnCompletion {
                    if (!cancelled) {
                        runBlocking {
                            jobsCompletion.decrement()
                        }
                    }
                    executingStepsGauge.decrementAndGet()
                    logger.trace(
                        "One job of minion $id was completed or cancelled (number of active jobs: ${jobsCompletion.get()})")
                }
                stepJobs.add(job)
                logger.trace("Job attached to minion $id (number of active jobs: ${jobsCompletion.get()})")
                // Put a new value to allow other coroutines to add a new job.
                if (executingLatch.isSuspended()) {
                    executingLatch.release()
                }
            }
        }
    }

    /**
     * Cancels all running coroutines of the current minion.
     */
    suspend fun cancel() {
        logger.trace("Cancelling minion $id")
        cancelled = true
        startLatch.release()
        eventLogger.debug("minion-cancellation-started", tags = mapOf("minion" to id))
        try {
            jobManagementMutex.withLock {
                for (job in completionJobs.plus(stepJobs).filter { it.isActive }) {
                    job.cancel(CancellationException())
                }
            }
            logger.trace("Cancellation of minions $id completed")
            eventLogger.debug("minion-cancellation-completed", tags = mapOf("minion" to id))
        } catch (e: Exception) {
            eventLogger.debug("minion-cancellation-completed", e, tags = mapOf("minion" to id))
        }
        executingLatch.release()
        jobsCompletion.release()
    }

    /**
     * Suspend the caller until the minion has started.
     */
    override suspend fun waitForStart() {
        startLatch.await()
    }

    /**
     * Waits for the minion to have all its jobs started and completed.
     */
    suspend fun join() {
        logger.trace("Joining minion $id")
        startLatch.await()
        executingLatch.await()
        jobsCompletion.await()
        logger.trace("Minion $id completed")
    }

    companion object {
        @JvmStatic
        val MAINTENANCE_PERIOD: Duration = Duration.ofMinutes(2)
    }
}