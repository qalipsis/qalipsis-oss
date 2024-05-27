/*
 * QALIPSIS
 * Copyright (C) 2023 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.meters

import com.tdunning.math.stats.TDigest
import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.Statistic
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.reporter.MeterReporter
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.LongAdder

/**
 * Implementation of [Timer] to track large number of short running events.
 *
 * @author Francisca Eze
 */
internal class TimerImpl(
    override val id: Meter.Id,
    private val meterReporter: MeterReporter,
    private val percentiles: Collection<Double>
) : Timer, Meter.ReportingConfiguration<Timer> {

    private var reportingConfigured = AtomicBoolean()

    @KTestable
    private val counter = LongAdder()

    @KTestable
    private val total = LongAdder()

    @KTestable
    private var tDigestBucket = TDigest.createAvlTreeDigest(100.0)

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

    override suspend fun buildSnapshot(timestamp: Instant): MeterSnapshot<*> =
        MeterSnapshotImpl(timestamp, this, this.measure())

    override fun count() = tDigestBucket.centroidCount().toLong()

    override fun totalTime(unit: TimeUnit?) = nanosToUnitConverter(total.toDouble(), unit)

    override fun max(unit: TimeUnit?) = nanosToUnitConverter(tDigestBucket.max, unit)

    override fun record(amount: Long, unit: TimeUnit?) {
        if (amount >= 0) {
            val amountInNanos = TimeUnit.NANOSECONDS.convert(amount, unit)
            counter.add(1)
            total.add(amountInNanos)
            tDigestBucket.add(amountInNanos.toDouble())
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
        return nanosToUnitConverter(tDigestBucket.quantile(percentile / 100), unit)
    }

    override suspend fun measure(): Collection<Measurement> =
        listOf(
            MeasurementMetric(count().toDouble(), Statistic.COUNT),
            MeasurementMetric(totalTime(BASE_TIME_UNIT), Statistic.TOTAL_TIME),
            MeasurementMetric(max(BASE_TIME_UNIT), Statistic.MAX),
            MeasurementMetric(mean(BASE_TIME_UNIT), Statistic.MEAN),
        ) + (percentiles.map {
            DistributionMeasurementMetric(percentile(it, BASE_TIME_UNIT), Statistic.PERCENTILE, it)
        })


    /**
     * Converts the total time to the specified format. Defaults to nanoseconds when not specified
     */
    private fun nanosToUnitConverter(totalTimeInNanos: Double, unit: TimeUnit?): Double {
        return when (unit) {
            TimeUnit.MICROSECONDS -> totalTimeInNanos / (NANOSECONDS_IN_MICROSECONDS)
            TimeUnit.MILLISECONDS -> totalTimeInNanos / (NANOSECONDS_IN_MILLISECONDS)
            TimeUnit.SECONDS -> totalTimeInNanos / (NANOSECONDS_IN_SECONDS)
            TimeUnit.MINUTES -> totalTimeInNanos / (NANOSECONDS_IN_MINUTES)
            TimeUnit.HOURS -> totalTimeInNanos / (NANOSECONDS_IN_HOURS)
            TimeUnit.DAYS -> totalTimeInNanos / (NANOSECONDS_IN_DAYS)
            TimeUnit.NANOSECONDS,
            null,
            -> totalTimeInNanos

            else -> {
                throw Exception("Unsupported time unit format $unit")
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TimerImpl
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    companion object {

        private const val NANOSECONDS_IN_MICROSECONDS = 1000L

        private const val NANOSECONDS_IN_MILLISECONDS = NANOSECONDS_IN_MICROSECONDS * 1000L

        private const val NANOSECONDS_IN_SECONDS = NANOSECONDS_IN_MILLISECONDS * 1000L

        private const val NANOSECONDS_IN_MINUTES = NANOSECONDS_IN_SECONDS * 60L

        private const val NANOSECONDS_IN_HOURS = NANOSECONDS_IN_MINUTES * 60L

        private const val NANOSECONDS_IN_DAYS = NANOSECONDS_IN_HOURS * 24L

        private val BASE_TIME_UNIT = TimeUnit.NANOSECONDS
    }
}