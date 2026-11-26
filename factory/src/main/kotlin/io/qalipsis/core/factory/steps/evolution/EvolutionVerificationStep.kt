/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.core.factory.steps.evolution

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.api.steps.verification.EvolutionDirection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.LongAdder

/**
 * Step verifying that, for each key, the values of the received records evolve in the expected [direction], with the
 * tolerance [expectedPercentile]. The step forwards its input unchanged and never fails on an invalid transition.
 *
 * Workflow:
 * 1. At start, the instance registers in the [countsRepository], which combines the counts of all the instances
 * of the step in the campaign (one per factory).
 * 2. Each record with a non-null key and value is compared with the previous value of its key, kept locally: from the
 * second value of a key on, each comparison is a transition, valid or invalid. Consecutive equal values are valid.
 * 3. The counts of transitions are accumulated locally and their deltas since the previous flush are sent in a single
 * call to the repository every [linger]. The combined percentage of valid transitions returned by the flush puts an
 * ERROR message on the step when below [expectedPercentile], and removes it otherwise.
 * 4. At stop, the instance flushes its last deltas and unregisters in a single call. Only the last instance of the
 * campaign reports the result: ERROR when below [expectedPercentile], INFO otherwise or when no transition was
 * recorded. The other instances remove their live message.
 *
 * The last values are local to the factory: when the records of a key are processed by several factories, each of
 * them verifies its own sub-sequence, only the counts are combined.
 *
 * @property keyExtractor extracts the key identifying the series of values, records with a null key are ignored
 * @property valueExtractor extracts the value to verify from the record, null values are ignored
 *
 * @author Eric Jessé
 */
class EvolutionVerificationStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val direction: EvolutionDirection,
    private val keyExtractor: (stepContext: StepContext<I, I>, input: I) -> Any?,
    private val valueExtractor: (input: I) -> Comparable<Any>?,
    private val expectedPercentile: Double,
    private val countsRepository: EvolutionCountsRepository,
    private val campaignReportLiveStateRegistry: CampaignReportLiveStateRegistry,
    private val coroutineScope: CoroutineScope,
    private val linger: Duration,
) : AbstractStep<I, I>(id, retryPolicy) {

    private val started = AtomicBoolean(false)

    /**
     * Last value of each key.
     */
    @KTestable
    private val lastValues = ConcurrentHashMap<Any, Comparable<Any>>()

    /**
     * Counts of transitions since the start, striped to not contend when recorded concurrently.
     */
    private val validTransitions = LongAdder()

    private val invalidTransitions = LongAdder()

    /**
     * Counts of transitions already sent to the [countsRepository], only accessed by the flush job, then by [stop]
     * once the job completed.
     */
    private var sentValid = 0L

    private var sentInvalid = 0L

    /**
     * Wakes up the flush job before the end of the [linger], when the step stops.
     */
    private val stopSignal = Channel<Unit>(Channel.CONFLATED)

    private var flushJob: Job? = null

    /**
     * Identifier of the message put by this instance on the step, to update or delete it.
     */
    @Volatile
    @KTestable
    private var messageId: String? = null

    private val directionName = direction.name.lowercase()

    override suspend fun start(context: StepStartStopContext) {
        if (started.compareAndSet(false, true)) {
            countsRepository.register(context.campaignKey, context.scenarioName, context.stepName)
            flushJob = coroutineScope.launch {
                while (started.get()) {
                    withTimeoutOrNull(linger.toMillis()) { stopSignal.receive() }
                    // Once stopped, the remaining deltas are sent with the unregistration.
                    if (started.get()) {
                        tryAndLogOrNull(log) { flush(context) }
                    }
                }
            }
        }
    }

    override suspend fun execute(context: StepContext<I, I>) {
        val input = context.receive()
        val key = keyExtractor(context, input)
        val value = key?.let { valueExtractor(input) }
        if (key != null && value != null) {
            track(key, value)
        }
        context.send(input)
    }

    /**
     * Compares [value] with the previous value of [key] and replaces it, atomically for the key.
     */
    private fun track(key: Any, value: Comparable<Any>) {
        lastValues.compute(key) { _, previous ->
            previous?.let { record(isValid(previous, value)) }
            value
        }
    }

    private fun isValid(previous: Comparable<Any>, current: Comparable<Any>) = when (direction) {
        EvolutionDirection.INCREASING -> current >= previous
        EvolutionDirection.DECREASING -> current <= previous
    }

    private fun record(valid: Boolean) = (if (valid) validTransitions else invalidTransitions).increment()

    /**
     * Sends the deltas since the previous flush to the [countsRepository] and verifies the combined percentage.
     * When the call fails, the deltas are sent with the next flush.
     */
    @KTestable
    private suspend fun flush(context: StepStartStopContext) {
        val valid = validTransitions.sum()
        val invalid = invalidTransitions.sum()
        if (valid + invalid > sentValid + sentInvalid) {
            val percentile = try {
                countsRepository.increment(
                    context.campaignKey, context.scenarioName, context.stepName, valid - sentValid, invalid - sentInvalid
                ).also {
                    sentValid = valid
                    sentInvalid = invalid
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn(e) { "The transitions of the step ${context.stepName} could not be flushed: ${e.message}" }
                null
            }
            percentile?.let { verify(context, it) }
        }
    }

    /**
     * Puts an ERROR message on the step when [percentile] is below the expectation, removes it otherwise.
     */
    private suspend fun verify(context: StepStartStopContext, percentile: Double) {
        if (percentile < expectedPercentile) {
            put(context, ReportMessageSeverity.ERROR, violationMessage(percentile))
        } else {
            delete(context)
        }
    }

    override suspend fun stop(context: StepStartStopContext) {
        if (started.compareAndSet(true, false)) {
            try {
                // Lets the flush job complete its current flush and leave, before the final one.
                stopSignal.trySend(Unit)
                flushJob?.join()
                flushJob = null
                val completion = countsRepository.unregister(
                    context.campaignKey,
                    context.scenarioName,
                    context.stepName,
                    validTransitions.sum() - sentValid,
                    invalidTransitions.sum() - sentInvalid
                )
                report(context, completion)
            } finally {
                lastValues.clear()
                validTransitions.reset()
                invalidTransitions.reset()
                sentValid = 0
                sentInvalid = 0
            }
        }
        super.stop(context)
    }

    /**
     * Reports the final result when this instance is the last one, otherwise removes its live message.
     */
    private suspend fun report(context: StepStartStopContext, completion: EvolutionCompletion) {
        val percentile = completion.percentile
        when {
            !completion.last -> delete(context)
            percentile == null -> put(
                context,
                ReportMessageSeverity.INFO,
                "No transition was recorded, the $directionName evolution could not be verified"
            )

            percentile < expectedPercentile -> put(context, ReportMessageSeverity.ERROR, violationMessage(percentile))
            else -> put(
                context,
                ReportMessageSeverity.INFO,
                "${percent(percentile)} of the transitions are $directionName " +
                        "(expected at least ${percent(expectedPercentile)})"
            )
        }
    }

    private fun violationMessage(percentile: Double) =
        "Only ${percent(percentile)} of the transitions are $directionName, " +
                "expected at least ${percent(expectedPercentile)}"

    private fun percent(value: Double) = String.format(Locale.ROOT, "%.2f%%", value)

    private suspend fun put(context: StepStartStopContext, severity: ReportMessageSeverity, message: String) {
        messageId = campaignReportLiveStateRegistry.put(
            campaignKey = context.campaignKey,
            scenarioName = context.scenarioName,
            stepName = context.stepName,
            severity = severity,
            messageId = messageId,
            message = message
        )
    }

    private suspend fun delete(context: StepStartStopContext) {
        messageId?.let { id ->
            campaignReportLiveStateRegistry.delete(
                campaignKey = context.campaignKey,
                scenarioName = context.scenarioName,
                stepName = context.stepName,
                messageId = id
            )
            messageId = null
        }
    }

    private companion object {

        @JvmStatic
        val log = logger()
    }
}
