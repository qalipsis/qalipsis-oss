package io.qalipsis.core.factories.orchestration

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.coroutines.contextualLaunch
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.factories.Minion
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
    override val campaignId: CampaignId,
    override val scenarioId: ScenarioId,
    override val rootDagId: DirectedAcyclicGraphId,
    pauseAtStart: Boolean = true,
    private val eventsLogger: EventsLogger,
    meterRegistry: MeterRegistry
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

    private val eventsTags = mapOf("campaign" to campaignId, "scenario" to scenarioId, "minion" to id)

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
            eventsLogger.info("minion.execution-complete", tags = eventsTags)
        }
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
        eventsLogger.info("minion.created", tags = eventsTags)
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
            eventsLogger.info("minion.running", tags = eventsTags)
        }
    }

    override fun isStarted(): Boolean {
        return !startLatch.isLocked
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
        eventsLogger.info("minion.cancellation.started", tags = eventsTags)
        try {
            for (job in runningJobs.values) {
                kotlin.runCatching {
                    job.cancel(CancellationException())
                }
            }
            log.trace { "Cancellation of minions $id completed" }
            eventsLogger.info("minion.cancellation.complete", tags = eventsTags)
        } catch (e: Exception) {
            eventsLogger.info("minion.cancellation.complete", e, tags = eventsTags)
        }
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
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MinionImpl) return false

        if (id != other.id) return false
        if (campaignId != other.campaignId) return false
        if (rootDagId != other.rootDagId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + campaignId.hashCode()
        result = 31 * result + rootDagId.hashCode()
        return result
    }


    companion object {

        private val log = logger()

    }
}
