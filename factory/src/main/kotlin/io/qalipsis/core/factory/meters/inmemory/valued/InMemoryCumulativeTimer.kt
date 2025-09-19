/*
 * QALIPSIS
 * Copyright (C) 2024 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.meters.inmemory.valued

import com.tdunning.math.stats.TDigest
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.meters.DistributionMeasurementMetric
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

/**
 * Implementation of [Timer] able to create step-period snapshots, while keeping the overall values over the campaign.
 *
 * It uses a round-robin strategy to avoid reading and writing at the same time.
 *
 * @author Eric Jessé
 */
internal class InMemoryCumulativeTimer(
    override val id: Meter.Id,
    private val meterReporter: MeterReporter,
    override val percentiles: Collection<Double>,
    private val bufferSize: Int = 3,
) : Timer, Meter.ReportingConfiguration<Timer> {

    private var reportingConfigured = AtomicBoolean()

    private val currentBucketHolder = AtomicInteger(0)

    private val buckets = List(bufferSize) { StepTimer(id, meterReporter, percentiles) }.toTypedArray()

    private val counter = LongAdder()

    private val total = LongAdder()

    private var tDigest: TDigest? = supplyIf(percentiles.isNotEmpty()) { TDigest.createMergingDigest(100.0) }

    private val max = AtomicLong(0)

    override fun report(configure: Meter.ReportingConfiguration<Timer>.() -> Unit): Timer {
        if (reportingConfigured.compareAndSet(false, true)) {
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

    override fun count(): Long {
        return counter.sum() + buckets[currentBucketHolder.get()].count()
    }

    override fun max(unit: TimeUnit?): Double {
        return microsToUnitConverter(max.get().toDouble(), unit).coerceAtLeast(
            buckets[currentBucketHolder.get()].max(unit)
        )
    }

    override fun mean(unit: TimeUnit?): Double {
        val count = count()
        return if (count == 0L) 0.0 else totalTime(unit) / count
    }

    override fun record(amount: Long, unit: TimeUnit?) {
        if (amount >= 0) {
            buckets[currentBucketHolder.get()].record(amount, unit)
            tDigest?.let {
                val amountInMicros = TimeUnit.MICROSECONDS.convert(amount, unit)
                synchronized(it) {
                    it.add(amountInMicros.toDouble())
                }
            }
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

    override fun totalTime(unit: TimeUnit?): Double {
        return microsToUnitConverter(total.toDouble(), unit) + buckets[currentBucketHolder.get()].totalTime(unit)
    }

    override suspend fun snapshot(timestamp: Instant): Collection<MeterSnapshot> {
        val currentBucket = currentBucketHolder.get()
        // Change to the next available bucket to avoid concurrent changes during the reading.
        return if (currentBucketHolder.compareAndSet(currentBucket, (currentBucket + 1) % bufferSize)) {
            buckets[currentBucket].let { timer ->
                counter.add(timer.count())
                total.add(timer.totalTime(null).toLong())
                val actualMax = max.get().coerceAtLeast(timer.max(null).toLong())
                max.set(actualMax)
                timer.snapshot(timestamp)
            }
        } else {
            listOf(MeterSnapshotImpl(timestamp, id, emptyList()))
        }
    }

    override suspend fun summarize(timestamp: Instant): Collection<MeterSnapshot> {
        snapshot(timestamp)
        val measures = listOf(
            MeasurementMetric(mean(BASE_TIME_UNIT), Statistic.MEAN),
            MeasurementMetric(counter.sum().toDouble(), Statistic.COUNT),
            MeasurementMetric(
                microsToUnitConverter(total.toDouble(), BASE_TIME_UNIT),
                Statistic.TOTAL_TIME
            ),
            MeasurementMetric(microsToUnitConverter(max.toDouble(), BASE_TIME_UNIT), Statistic.MAX),
        ) + (percentiles.map {
            DistributionMeasurementMetric(percentile(it, BASE_TIME_UNIT), Statistic.PERCENTILE, it)
        })

        return listOf(MeterSnapshotImpl(timestamp, id.copy(tags = id.tags + ("scope" to "campaign")), measures))
    }

    override fun percentile(percentile: Double, unit: TimeUnit?): Double {
        return tDigest?.let { microsToUnitConverter(it.quantile(percentile / 100), unit) } ?: 0.0
    }

    /**
     * Converts the total time to the specified format. Defaults to nanoseconds when not specified.
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

        other as InMemoryCumulativeTimer
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
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