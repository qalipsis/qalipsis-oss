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

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.Statistic
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.reporter.MeterReporter
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.LongAdder
import kotlin.math.ceil

/**
 * Implementation of [Timer].
 */
internal class TimerImpl(
    override val id: Meter.Id,
    private val meterReporter: MeterReporter,
) : Timer, Meter.ReportingConfiguration<Timer> {

    private var reportingConfigured = AtomicBoolean()

    private val counter = LongAdder()

    private val total = LongAdder()

    private val C0 = 1L

    private val C1 = C0 * 1000L

    private val C2 = C1 * 1000L

    private val C3 = C2 * 1000L

    private val C4 = C3 * 60L

    private val C5 = C4 * 60L

    private val C6 = C5 * 24L

    private val bucket = mutableListOf<Long>()

    private var isBucketSorted = false

    @KTestable
    private var maxBucket = TreeSet(
        Comparator<Long> { a, b -> if (b!! > a!!) 1 else -1 }
    )

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

    override fun count() = counter.toLong()

    override fun totalTime(unit: TimeUnit?) = nanosToUnitConverter(total.toLong(), unit)

    override fun max(unit: TimeUnit?): Double {
        return nanosToUnitConverter(maxBucket.first(), unit)
    }

    override fun record(amount: Long, unit: TimeUnit?) {
        if (amount >= 0) {
            val amountInNanos = TimeUnit.NANOSECONDS.convert(amount, unit)
            maxBucket.add(amountInNanos)
            //@TODO not sure about the histogram.record. Basically it adds a value to an AtomicLongArrayBucket.
            counter.add(1)
            total.add(amountInNanos)
            bucket.add(amountInNanos)
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

    /**
     * [0-1]
     */
    override fun percentile(percentile: Double, unit: TimeUnit?): Double {
        if (!isBucketSorted) {
            bucket.sort()
            isBucketSorted = true
        }
        val index = ceil(percentile * bucket.size).toInt()

        return nanosToUnitConverter(bucket[index - 1], unit)
    }

    override fun histogramCountAtValue(valueNanos: Long): Double {
        if (!isBucketSorted) {
            bucket.sort()
            isBucketSorted = true
        }
        var count = 0
        for (values in bucket) {
            if (values <= valueNanos) {
                count++
            } else {
                break
            }
        }

        return count.toDouble()
    }

    //@TODO using nanoseconds as the default timeunit
    override suspend fun measure(): Collection<Measurement> =
        listOf(
            MeasurementMetric(count().toDouble(), Statistic.COUNT),
            MeasurementMetric(totalTime(BASE_TIME_UNIT), Statistic.TOTAL_TIME),
            MeasurementMetric(max(BASE_TIME_UNIT), Statistic.MAX),
            MeasurementMetric(mean(BASE_TIME_UNIT), Statistic.MEAN)
        )


    /**
     * Converts the total time to the specified format. Defaults to nanoseconds when not specified
     */
    //@TODO improve implementation for the converter
    private fun nanosToUnitConverter(totalTimeInNanos: Long, unit: TimeUnit?): Double {
        return when (unit) {
            TimeUnit.NANOSECONDS -> totalTimeInNanos.toDouble()
            TimeUnit.MICROSECONDS -> totalTimeInNanos / (C1 / C0).toDouble()
            TimeUnit.MILLISECONDS -> totalTimeInNanos / (C2 / C0).toDouble()
            TimeUnit.SECONDS -> totalTimeInNanos / (C3 / C0).toDouble()
            TimeUnit.MINUTES -> totalTimeInNanos / (C4 / C0).toDouble()
            TimeUnit.HOURS -> totalTimeInNanos / (C5 / C0).toDouble()
            TimeUnit.DAYS -> totalTimeInNanos / (C6 / C0).toDouble()
            null -> totalTimeInNanos.toDouble()
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
        private val BASE_TIME_UNIT = TimeUnit.NANOSECONDS
    }
}