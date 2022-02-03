package io.qalipsis.core.factory.steps.join

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.context.CorrelationRecord
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.api.sync.Latch
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.exceptions.NotInitializedStepException
import kotlinx.coroutines.*
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap


/**
 * Step to correlate flows from several previous [Step]s into a single one. While this steps technically has several
 * parents, only one is considered at the primary, from which will the current one will inherit the context.
 *
 * This step uses the same mechanism than [io.qalipsis.core.factory.steps.singleton.SingletonProxyStep] to receive
 * data from secondary steps.
 *
 * Correlation keys supports the [io.qalipsis.api.context.MinionId] and the received value. When using the
 * [io.qalipsis.api.context.MinionId], the step can be used to correlate data of different DAGs of the same minion.
 *
 * The step execution is suspended until either the timeout is reached or values from all parent steps are provided.
 *
 * @author Eric Jessé
 */
internal class InnerJoinStep<I, O>(
    id: StepId,
    private val coroutineScope: CoroutineScope,
    /**
     * Specification of the key extractor based upon the value received from the left step.
     */
    private val leftKeyExtractor: (record: CorrelationRecord<I>) -> Any?,
    /**
     * Configuration for the consumption and correlation from right steps.
     */
    private val rightCorrelations: Collection<RightCorrelation<out Any>>,
    /**
     * Timeout, after which the values received but not forwarded are evicted from the cache.
     */
    cacheTimeout: Duration,
    /**
     * Statement to convert the list of values into the output.
     */
    private val outputSupplier: (I, Map<StepId, Any?>) -> O

) : AbstractStep<I, O>(id, null) {

    private val consumptionJobs = mutableListOf<Job>()

    private val cache: AsyncLoadingCache<Any, CacheEntry>

    init {
        var cacheBuilder = Caffeine.newBuilder()
        if (!cacheTimeout.isNegative && !cacheTimeout.isZero) {
            cacheBuilder = cacheBuilder.expireAfterWrite(cacheTimeout)
        }
        cache = cacheBuilder.buildAsync { entryKey -> CacheEntry(entryKey, rightCorrelations.size) }
    }

    private var running = false

    override suspend fun start(context: StepStartStopContext) {
        // Starts the coroutines to buffer the data coming from the right steps into the local cache.
        val stepId = this.id
        rightCorrelations.forEach { corr ->
            @Suppress("UNCHECKED_CAST")
            val keyExtractor = (corr.keyExtractor as (CorrelationRecord<out Any>) -> Any?)
            consumptionJobs.add(
                coroutineScope.launch {
                    log.debug { "Starting the coroutine buffering right records from step ${corr.sourceStepId}" }
                    val subscription = corr.topic.subscribe(stepId)
                    while (subscription.isActive()) {
                        val record = subscription.pollValue()
                        keyExtractor(record)?.let { key ->
                            log.trace { "Adding right record to cache with key '$key' as ${key::class}" }
                            cache.get(key).thenAccept { entry ->
                                coroutineScope.launch { entry.addValue(record.stepId, record.value) }
                            }
                        }
                    }
                    log.debug { "Leaving the coroutine buffering right records" }
                }
            )
        }
        running = true
    }

    override suspend fun stop(context: StepStartStopContext) {
        running = false
        consumptionJobs.forEach { it.cancel() }
        rightCorrelations.forEach { it.topic.close() }
    }

    override suspend fun execute(context: StepContext<I, O>) {
        if (!running) throw NotInitializedStepException()

        val input = context.receive()
        // Extract the key from the left side and search or wait for an available equivalent value coming from the right side.
        leftKeyExtractor(CorrelationRecord(context.minionId, context.previousStepId!!, input))
            ?.let { key ->
                try {
                    log.trace { "Searching correlation values for key '$key' as ${key::class}" }
                    val latch = Latch(true, "inner-join-step-${id}")
                    cache.get(key).thenAccept { entry ->
                        coroutineScope.launch {
                            val secondaryValues = entry.get()
                            log.trace { "Forwarding a correlated set of values" }
                            context.send(outputSupplier(input, secondaryValues))
                            latch.release()
                        }
                    }
                    latch.await()
                } finally {
                    log.trace { "Invalidating cache with key $key as ${key::class}" }
                    cache.synchronous().invalidate(key)
                }
            }
    }

    @KTestable
    private fun hasKeyInCache(key: Any?): Boolean = key?.let { cache.asMap().containsKey(it) } ?: false

    @KTestable
    private fun isCacheEmpty(): Boolean = cache.asMap().isEmpty()

    companion object {
        @JvmStatic
        private val log = logger()
    }

    private data class CacheEntry(
        /**
         * Common correlation key for all the values.
         */
        val correlationKey: Any,
        /**
         * Number of expected values from secondary steps.
         */
        val secondaryValuesCount: Int
    ) {
        /**
         * Mutex to suspend the calls the received() until all the values are received.
         */
        private val countLatch = SuspendedCountLatch(secondaryValuesCount.toLong())

        /**
         * Values received from the secondary [io.qalipsis.api.steps.Step]s so far, indexed by the source [StepId].
         */
        private val secondaryValues: MutableMap<StepId, Any?> = ConcurrentHashMap()

        /**
         * Adds a value coming from a secondary [io.qalipsis.api.steps.Step].
         *
         * @param stepId ID of the [io.qalipsis.api.steps.Step] that generated the value.
         * @param value Actual value to provide for the correlation.
         */
        suspend fun addValue(stepId: StepId, value: Any?) {
            secondaryValues[stepId] = value
            countLatch.decrement()
        }

        /**
         * Provides the values from the secondary steps.
         *
         * This method suspends the call until all the values are received.
         */
        suspend fun get(): Map<StepId, Any?> {
            if (secondaryValues.size < secondaryValuesCount) {
                countLatch.await()
            }
            return secondaryValues
        }
    }
}
