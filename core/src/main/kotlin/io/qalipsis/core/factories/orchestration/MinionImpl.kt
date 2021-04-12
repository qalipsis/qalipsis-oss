package io.qalipsis.core.factories.orchestration

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.factories.Minion
import io.qalipsis.api.sync.Latch
import io.qalipsis.api.sync.SuspendedCountLatch
import kotlinx.coroutines.*
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
    override val campaignId: CampaignId,
    override val scenarioId: ScenarioId,
    override val dagId: DirectedAcyclicGraphId,
    pauseAtStart: Boolean = true,
    private val eventsLogger: EventsLogger,
    meterRegistry: MeterRegistry
) : Minion {

    /**
     * Latch to suspend caller while the minion is not started.
     */
    private val startLatch = Latch(true)

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
        onCompleteHooks.forEach { it() }
        eventsLogger.info("minion.execution-complete", tags = mapOf("campaign" to campaignId, "minion" to id))
    }

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private val executingStepsGauge: AtomicInteger =
        meterRegistry.gauge(
            "minion-running-steps", listOf(Tag.of("campaign", campaignId), Tag.of("minion", id)),
            AtomicInteger()
        )

    /**
     * Computed count of active steps.
     */
    val stepsCount: Int
        get() = executingStepsGauge.get()

    init {
        eventsLogger.info("minion.created", tags = mapOf("campaign" to campaignId, "minion" to id))
        if (!pauseAtStart) {
            doStart()
        }
    }

    override fun onComplete(block: suspend (() -> Unit)) {
        onCompleteHooks.add(block)
    }

    override suspend fun start() {
        if (!isStarted()) {
            startLatch.release()
            eventsLogger.info("minion.running", tags = mapOf("campaign" to campaignId, "minion" to id))
        }
    }

    private fun doStart() {
        if (!isStarted()) {
            startLatch.cancel()
            eventsLogger.info("minion.running", tags = mapOf("campaign" to campaignId, "minion" to id))
        }
    }

    override fun isStarted(): Boolean {
        return !startLatch.isLocked
    }

    override suspend fun launch(
        scope: CoroutineScope?,
        context: CoroutineContext?,
        countLatch: SuspendedCountLatch?,
        block: suspend CoroutineScope.() -> Unit
    ): Job? {
        return if (cancelled) {
            log.trace("Minion $id was cancelled, no new job can be started")
            null
        } else {
            log.trace("Adding a job to minion $id (number of active jobs: ${runningJobsLatch.get()})")
            executingStepsGauge.incrementAndGet()
            runningJobsLatch.increment()
            countLatch?.increment()
            val jobId = jobIndex.getAndIncrement()
            (scope ?: GlobalScope).launch(context ?: GlobalScope.coroutineContext) {
                waitForStart()
                try {
                    this.block()
                } finally {
                    runningJobs.remove(jobId)
                    runningJobsLatch.decrement()
                    executingStepsGauge.decrementAndGet()
                    countLatch?.decrement()
                    log.trace(
                        "One job of minion $id was completed or cancelled (number of active jobs: ${runningJobsLatch.get()})"
                    )
                }
            }.also {
                runningJobs[jobId] = it
            }
        }
    }

    override suspend fun cancel() {
        log.trace("Cancelling minion $id")
        cancelled = true
        startLatch.cancel()
        eventsLogger.info("minion.cancellation.started", tags = mapOf("campaign" to campaignId, "minion" to id))
        try {
            for (job in runningJobs.values) {
                kotlin.runCatching {
                    job.cancel(CancellationException())
                }
            }
            log.trace("Cancellation of minions $id completed")
            eventsLogger.info("minion.cancellation.complete", tags = mapOf("campaign" to campaignId, "minion" to id))
        } catch (e: Exception) {
            eventsLogger.info(
                "minion.cancellation.complete", e,
                tags = mapOf("campaign" to campaignId, "minion" to id)
            )
        }
        runningJobsLatch.cancel()
    }

    override suspend fun waitForStart() {
        startLatch.await()
    }

    override suspend fun join() {
        log.trace("Joining minion $id")
        startLatch.await()
        runningJobsLatch.awaitActivity()
        runningJobsLatch.await()
        log.trace("Minion $id completed")
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

        private val log = logger()

    }
}
