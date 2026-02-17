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

package io.qalipsis.core.factory.steps.join

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.context.CorrelationRecord
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.runtime.Minion
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.core.exceptions.NotInitializedStepException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger


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
class InnerJoinStep<I, O>(
    id: StepName,
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
    private val outputSupplier: (I, Map<StepName, Any?>) -> O

) : AbstractStep<I, O>(id, null) {

    private val consumptionJobs = mutableListOf<Job>()

    private val cache: AsyncLoadingCache<Any, CacheEntry>

    init {
        var cacheBuilder = Caffeine.newBuilder()
        if (!cacheTimeout.isNegative && !cacheTimeout.isZero) {
            cacheBuilder = cacheBuilder.expireAfterWrite(cacheTimeout)
        }
        cache = cacheBuilder.buildAsync { _ -> CacheEntry(rightCorrelations.size) }
    }

    @Volatile
    private var running = false

    override suspend fun start(context: StepStartStopContext) {
        // Starts the coroutines to buffer the data coming from the right steps into the local cache.
        val stepName = this.name
        rightCorrelations.forEach { corr ->
            @Suppress("UNCHECKED_CAST")
            val keyExtractor = (corr.keyExtractor as (CorrelationRecord<out Any>) -> Any?)
            consumptionJobs.add(
                coroutineScope.launch {
                    log.debug { "Starting the coroutine buffering right records from step ${corr.sourceStepName}" }
                    val subscription = corr.topic.subscribe(stepName)
                    while (subscription.isActive()) {
                        try {
                            val record = subscription.pollValue()
                            keyExtractor(record)?.let { key ->
                                log.trace { "Adding right record to cache with key '$key' as ${key::class.qualifiedName}" }
                                val entry = cache.synchronous().get(key)
                                entry.addValue(record.stepName, record.value)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            log.warn(e) { "Error processing right record from step ${corr.sourceStepName}" }
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
        consumptionJobs.clear()
        rightCorrelations.forEach { it.topic.close() }
    }

    override suspend fun execute(minion: Minion, context: StepContext<I, O>) {
        if (!running) throw NotInitializedStepException()

        val input = context.receive()
        // Extract the key from the left side and search or wait for an available equivalent value coming from the right side.
        leftKeyExtractor(CorrelationRecord(context.minionId, context.previousStepName!!, input))
            ?.let { key ->
                try {
                    log.trace { "Searching correlation values for key '$key' as ${key::class}" }
                    val entry = cache.synchronous().get(key)
                    val secondaryValues = entry.get()
                    log.trace { "Forwarding a correlated set of values to context $context" }
                    context.send(outputSupplier(input, secondaryValues))
                } finally {
                    log.trace { "Invalidating cache with key $key as ${key::class}" }
                    cache.synchronous().invalidate(key)
                }
            }
    }

    override suspend fun execute(context: StepContext<I, O>) {
        // This method should never be called.
        throw NotImplementedError()
    }

    @KTestable
    private fun hasKeyInCache(key: Any?): Boolean = key?.let { cache.asMap().containsKey(it) } ?: false

    @KTestable
    private fun isCacheEmpty(): Boolean = cache.asMap().isEmpty()

    companion object {
        @JvmStatic
        private val log = logger()
    }

    private class CacheEntry(
        /**
         * Number of expected values from secondary steps.
         */
        val secondaryValuesCount: Int
    ) {
        /**
         * Atomic counter tracking the remaining number of values to receive.
         */
        private val remaining = AtomicInteger(secondaryValuesCount)

        /**
         * Deferred completed when all secondary values have been received.
         */
        private val completed = CompletableDeferred<Unit>()

        /**
         * Values received from the secondary [io.qalipsis.api.steps.Step]s so far, indexed by the source [StepName].
         */
        private val secondaryValues = HashMap<StepName, Any?>()

        /**
         * Adds a value coming from a secondary [io.qalipsis.api.steps.Step].
         *
         * @param stepName ID of the [io.qalipsis.api.steps.Step] that generated the value.
         * @param value Actual value to provide for the correlation.
         */
        @Synchronized
        fun addValue(stepName: StepName, value: Any?) {
            if (!secondaryValues.containsKey(stepName)) {
                secondaryValues[stepName] = value
                if (remaining.decrementAndGet() == 0) {
                    completed.complete(Unit)
                }
            }
        }

        /**
         * Provides the values from the secondary steps.
         *
         * This method suspends the call until all the values are received.
         */
        suspend fun get(): Map<StepName, Any?> {
            completed.await()
            return secondaryValues
        }
    }
}
