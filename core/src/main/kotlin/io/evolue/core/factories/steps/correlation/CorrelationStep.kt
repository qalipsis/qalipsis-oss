package io.evolue.core.factories.steps.correlation

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import io.evolue.api.annotations.VisibleForTest
import io.evolue.api.context.CorrelationRecord
import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.steps.AbstractStep
import io.evolue.api.sync.SuspendedCountLatch
import io.evolue.core.exceptions.NotInitializedStepException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration


/**
 * Step to correlate flows from several previous [Step]s into a single one. While this steps technically has several
 * parents, only one is considered at the primary, from which will the current one will inherit the context.
 *
 * This step uses the same mechanism than [io.evolue.core.factories.steps.singleton.SingletonProxyStep] to receive
 * data from secondary steps.
 *
 * Correlation keys supports the [io.evolue.api.context.MinionId] and the received value. When using the
 * [io.evolue.api.context.MinionId], the step can be used to correlate data of different DAGs of the same minion.
 *
 * The step execution is suspended until either the timeout is reached or values from all parent steps are provided.
 *
 * @author Eric Jessé
 */
internal class CorrelationStep<I>(
        id: StepId,

        /**
         * Specification of the key extractor based upon the value received from the primary parent.
         */
        private val correlationKeyExtractor: ((record: CorrelationRecord<I>) -> Any?),

        /**
         * Configuration for the consumption and correlation from secondary parents.
         */
        private val secondaryCorrelations: Collection<SecondaryCorrelation<out Any>>,

        /**
         * Timeout, after which the values received but not forwarded are evicted from the cache.
         */
        cacheTimeout: Duration

) : AbstractStep<I, Array<Any>>(id, null) {

    private val consumptionJobs = mutableListOf<Job>()

    private val cache: LoadingCache<Any, CacheEntry> = Caffeine.newBuilder()
        .expireAfterWrite(cacheTimeout)
        .build { entryKey -> CacheEntry(entryKey, secondaryCorrelations.size) }

    private var initialized = false

    override suspend fun init() {
        // Coroutines to buffer the data coming from the remote job into the local cache.
        val stepId = this.id
        secondaryCorrelations.forEach { corr ->
            val keyExtractor = (corr.keyExtractor as (CorrelationRecord<out Any>) -> Any?)
            consumptionJobs.add(GlobalScope.launch {
                log.debug("Starting the coroutine to buffer remote records")
                val subscription = corr.topic.subscribe(stepId)
                while (subscription.isActive()) {
                    val record = subscription.pollValue()
                    log.trace("Received record $record")
                    keyExtractor(record)?.let { key ->
                        log.trace("Adding record $record to cache with key $key")
                        cache.get(key)!!.addValue(record.stepId, record.value)
                    }
                }
                log.debug("Leaving the coroutine to buffer remote records")
            })
        }
        initialized = true
    }

    override suspend fun destroy() {
        consumptionJobs.forEach { it.cancel() }
        secondaryCorrelations.forEach { it.topic.close() }
    }

    override suspend fun execute(context: StepContext<I, Array<Any>>) {
        if (!initialized) throw NotInitializedStepException()

        val input = context.input.receive()
        correlationKeyExtractor(CorrelationRecord(context.minionId, context.parentStepId!!, input))
            ?.let { key ->
                try {
                    val cachedSecondaryValues = cache.get(key)!!.receive()
                    val values =
                        arrayListOf(input) + secondaryCorrelations.map { cachedSecondaryValues[it.sourceStepId] }
                    (context.output as Channel<Any?>).send(values.toTypedArray())
                } finally {
                    log.trace("Invalidating cache with key $key")
                    cache.invalidate(key)
                }
            }
    }

    @VisibleForTest
    internal fun hasKeyInCache(key: Any?): Boolean = cache.asMap().containsKey(key)

    @VisibleForTest
    internal fun isCacheEmpty(): Boolean = cache.asMap().isEmpty()

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
        val countLatch = SuspendedCountLatch(secondaryValuesCount.toLong())

        /**
         * Mutex to avoid concurrent changes of the data.
         */
        val secondaryValuesMutex = Mutex(false)

        /**
         * Values received from the secondary [io.evolue.api.steps.Step]s so far, indexed by the source [StepId].
         */
        val secondaryValues: MutableMap<StepId, Any?> = mutableMapOf()

        /**
         * Add a value coming from a secondary [io.evolue.api.steps.Step].
         *
         * @param stepId ID of the [io.evolue.api.steps.Step] that generated the value.
         * @param value Actual value to provide for the correlation.
         */
        suspend fun addValue(stepId: StepId, value: Any?) {
            // Don't process the data when the mutex was already unlocked.
            secondaryValuesMutex.withLock {
                secondaryValues[stepId] = value
                countLatch.decrement()
            }
        }

        /**
         * Provide the values from the secondary steps.
         * This method suspends the call until all the values are received.
         */
        suspend fun receive(): Map<StepId, Any?> {
            countLatch.await()
            return secondaryValues
        }

        companion object {
            @JvmStatic
            private val log = logger()
        }
    }
}
