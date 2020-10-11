package io.qalipsis.core.factories.orchestration

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.factories.Minion
import io.qalipsis.api.sync.SuspendedCountLatch
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
        override val id: MinionId,
        override val campaignId: CampaignId,
        override val dagId: DirectedAcyclicGraphId,
        pauseAtStart: Boolean = true,
        private val eventsLogger: EventsLogger,
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
     * List of the [step][io.qalipsis.api.steps.Step] jobs to be executed for the current minion.
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
    private val jobsCompletion = SuspendedCountLatch {
        onCompleteHooks.forEach { it() }
        eventsLogger.info("minion-completed", tags = mapOf("campaign" to campaignId, "minion" to id))
    }

    private val logger = logger()

    /**
     * Mutex to sync operations on the jobs.
     */
    private val jobManagementMutex = Mutex()

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private val executingStepsGauge: AtomicInteger =
        meterRegistry.gauge("minion-executing-steps", listOf(Tag.of("campaign", campaignId), Tag.of("minion", id)),
                AtomicInteger())

    private val maintenancePeriodTimer = meterRegistry.timer("minion-maintenance", "campaign", campaignId, "minion", id)

    /**
     * Computed count of active steps.
     */
    val stepsCount: Int
        get() = executingStepsGauge.get()

    init {
        eventsLogger.info("minion-created", tags = mapOf("campaign" to campaignId, "minion" to id))
        if (!pauseAtStart) {
            runBlocking {
                start()
            }
        }

        // Start maintenance tasks.
        GlobalScope.launch {
            eventsLogger.trace("minion-maintenance-routine-started",
                    tags = mapOf("campaign" to campaignId, "minion" to id))
            startLatch.await()
            delay(maintenancePeriod.toMillis())
            while (!cancelled) {
                delay(maintenancePeriod.toMillis())
                eventsLogger.trace("minion-maintenance-operation-started",
                        tags = mapOf("campaign" to campaignId, "minion" to id))
                logger.trace("Running maintenance operations...")
                val start = System.nanoTime()
                jobManagementMutex.withLock {
                    stepJobs.removeIf { !it.isActive }
                    completionJobs.removeIf { !it.isActive }
                }
                Duration.ofNanos(System.nanoTime() - start).let {
                    maintenancePeriodTimer.record(it)
                    eventsLogger.trace("minion-maintenance-operation-completed", it,
                            tags = mapOf("campaign" to campaignId, "minion" to id))
                    logger.trace("Maintenance operations executed in $it")
                }
            }
            eventsLogger.trace("minion-maintenance-routine-stopped",
                    tags = mapOf("campaign" to campaignId, "minion" to id))
        }
    }

    override fun onComplete(block: suspend (() -> Unit)) {
        onCompleteHooks.add(block)
    }

    override suspend fun start() {
        if (!isStarted()) {
            startLatch.release()
            eventsLogger.info("minion-started", tags = mapOf("campaign" to campaignId, "minion" to id))
        }
    }


    override fun isStarted(): Boolean {
        return !startLatch.isSuspended()
    }

    override suspend fun attach(job: Job) {
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

    override suspend fun cancel() {
        logger.trace("Cancelling minion $id")
        cancelled = true
        startLatch.release()
        eventsLogger.info("minion-cancellation-started", tags = mapOf("campaign" to campaignId, "minion" to id))
        try {
            jobManagementMutex.withLock {
                for (job in completionJobs.plus(stepJobs).filter { it.isActive }) {
                    job.cancel(CancellationException())
                }
            }
            logger.trace("Cancellation of minions $id completed")
            eventsLogger.info("minion-cancellation-completed", tags = mapOf("campaign" to campaignId, "minion" to id))
        } catch (e: Exception) {
            eventsLogger.info("minion-cancellation-completed", e,
                    tags = mapOf("campaign" to campaignId, "minion" to id))
        }
        executingLatch.release()
        jobsCompletion.release()
    }

    override suspend fun waitForStart() {
        startLatch.await()
    }

    override suspend fun join() {
        logger.trace("Joining minion $id")
        startLatch.await()
        executingLatch.await()
        jobsCompletion.await()
        logger.trace("Minion $id completed")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MinionImpl) return false

        if (id != other.id) return false
        if (campaignId != other.campaignId) return false
        if (dagId != other.dagId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + campaignId.hashCode()
        result = 31 * result + dagId.hashCode()
        return result
    }


    companion object {
        @JvmStatic
        val MAINTENANCE_PERIOD: Duration = Duration.ofMinutes(2)
    }
}
