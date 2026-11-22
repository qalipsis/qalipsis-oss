/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.meters.inmemory.valued

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.Statistic
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.factory.meters.MeterSnapshotImpl
import io.qalipsis.core.reporter.MeterReporter
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

/**
 * Implementation of [Timer] to track large number of short running events, that resets when snapshots are generated.
 *
 * @author Francisca Eze
 */
internal class StepTimer(
    override val id: Meter.Id,
    private val meterReporter: MeterReporter,
    override val percentiles: Collection<Double>,
) : Timer, Meter.ReportingConfiguration<Timer> {

    private var reportingConfigured = AtomicBoolean()

    @KTestable
    private val counter = LongAdder()

    @KTestable
    private val total = LongAdder()

    private val percentilesRecorder: PercentilesRecorder? = supplyIf(percentiles.isNotEmpty()) { PercentilesRecorder() }

    private val max = AtomicLong(0)

    override fun report(configure: Meter.ReportingConfiguration<Timer>.() -> Unit): Timer {
        if (!reportingConfigured.compareAndExchange(false, true)) {
            this.configure()
        }
        return this
    }

    override fun display(
        format: String,
        severity: Number.() -> ReportMessageSeverity,
        row: Short,
        column: Short,
        toNumber: Timer.() -> Number,
    ) {
        meterReporter.report(this, format, severity, row, column, toNumber)
    }

    override suspend fun snapshot(timestamp: Instant): Collection<MeterSnapshot> =
        listOf(MeterSnapshotImpl(timestamp, id.copy(tags = id.tags + ("scope" to "period")), measure()))

    @KTestable
    private fun measure(): Collection<Measurement> {
        val result = listOf(
            MeasurementMetric(mean(BASE_TIME_UNIT), Statistic.MEAN),
            MeasurementMetric(counter.sumThenReset().toDouble(), Statistic.COUNT),
            MeasurementMetric(
                microsToUnitConverter(total.sumThenReset().toDouble(), BASE_TIME_UNIT),
                Statistic.TOTAL_TIME
            ),
            MeasurementMetric(microsToUnitConverter(max.getAndSet(0).toDouble(), BASE_TIME_UNIT), Statistic.MAX),
        ) + (percentilesRecorder?.drain(percentiles).orEmpty().zip(percentiles) { valueInMicros, percentile ->
            DistributionMeasurementMetric(
                microsToUnitConverter(valueInMicros, BASE_TIME_UNIT),
                Statistic.PERCENTILE,
                percentile
            )
        })
        return result
    }

    override suspend fun summarize(timestamp: Instant) =
        snapshot(timestamp).map { it.duplicate(it.meterId.copy(tags = it.meterId.tags + ("scope" to "campaign"))) }

    override fun count() = counter.toLong()

    override fun totalTime(unit: TimeUnit?) = microsToUnitConverter(total.toDouble(), unit)

    override fun max(unit: TimeUnit?) = microsToUnitConverter(max.get().toDouble(), unit)

    override fun mean(unit: TimeUnit?): Double {
        val count = count()
        return if (count == 0L) 0.0 else totalTime(unit) / count
    }

    override fun record(amount: Long, unit: TimeUnit?) {
        if (amount >= 0) {
            val amountInMicros = TimeUnit.MICROSECONDS.convert(amount, unit)
            var curMax: Long
            do {
                curMax = max.get()
                // This do / while loop ensures that the max is only updated if not a higher value was set in the meantime.
            } while (curMax < amountInMicros && !max.compareAndSet(curMax, amountInMicros))
            total.add(amountInMicros)
            counter.add(1)
            percentilesRecorder?.record(amountInMicros)
        }
    }

    override suspend fun <T> record(block: suspend () -> T): T {
        val preExecutionTimeInNanos = System.nanoTime()
        return try {
            block()
        } finally {
            val postExecutionTimeInNanos = System.nanoTime()
            record(postExecutionTimeInNanos - preExecutionTimeInNanos, TimeUnit.NANOSECONDS)
        }
    }

    override fun percentile(percentile: Double, unit: TimeUnit?): Double {
        return percentilesRecorder?.let { microsToUnitConverter(it.percentile(percentile), unit) } ?: 0.0
    }

    /**
     * Converts the total time to the specified format. Defaults to nanoseconds when not specified
     */
    private fun microsToUnitConverter(totalTimeInMicros: Double, unit: TimeUnit?): Double {
        return when (unit) {
            TimeUnit.NANOSECONDS -> totalTimeInMicros / (MICROSECONDS_IN_NANOSECONDS)
            TimeUnit.MICROSECONDS -> totalTimeInMicros
            TimeUnit.MILLISECONDS -> totalTimeInMicros / (MICROSECONDS_IN_MILLISECONDS)
            TimeUnit.SECONDS -> totalTimeInMicros / (MICROSECONDS_IN_SECONDS)
            TimeUnit.MINUTES -> totalTimeInMicros / (MICROSECONDS_IN_MINUTES)
            TimeUnit.HOURS -> totalTimeInMicros / (MICROSECONDS_IN_HOURS)
            TimeUnit.DAYS -> totalTimeInMicros / (MICROSECONDS_IN_DAYS)
            null -> totalTimeInMicros
            else -> {
                throw Exception("Unsupported time unit format $unit")
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StepTimer
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "StepTimer(id=$id)"
    }

    companion object {

        private const val MICROSECONDS_IN_NANOSECONDS = 1 / 1000.0

        private const val MICROSECONDS_IN_MILLISECONDS = 1000.0

        private const val MICROSECONDS_IN_SECONDS = MICROSECONDS_IN_MILLISECONDS * 1000.0

        private const val MICROSECONDS_IN_MINUTES = MICROSECONDS_IN_SECONDS * 60L

        private const val MICROSECONDS_IN_HOURS = MICROSECONDS_IN_MINUTES * 60L

        private const val MICROSECONDS_IN_DAYS = MICROSECONDS_IN_HOURS * 24L

        private val BASE_TIME_UNIT = TimeUnit.MICROSECONDS
    }
}