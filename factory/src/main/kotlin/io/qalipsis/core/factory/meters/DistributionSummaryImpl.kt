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
import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.Statistic
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.reporter.MeterReporter
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.DoubleAdder
import java.util.concurrent.atomic.LongAdder
import kotlin.math.ceil

/**
 *
 */
internal class DistributionSummaryImpl(
    override val id: Meter.Id,
    private val meterReporter: MeterReporter,
) : DistributionSummary, Meter.ReportingConfiguration<DistributionSummary> {

    private var reportingConfigured = AtomicBoolean()

    private val count = LongAdder()

    private val total = DoubleAdder()

    private val bucket = mutableListOf<Double>()

    private var isBucketSorted = false

    @KTestable
    private var maxBucket = TreeSet(
        Comparator<Double> { a, b -> if (b!! > a!!) 1 else -1 }
    )

    override fun report(configure: Meter.ReportingConfiguration<DistributionSummary>.() -> Unit): DistributionSummary {
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
        toNumber: DistributionSummary.() -> Number,
    ) {
        meterReporter.report(this, format, severity, row, column, toNumber)
    }

    //@TODO take out this poll steps
    override fun count(): Long = count.toLong()

    override fun totalAmount(): Double = total.toDouble()

    override fun max(): Double = maxBucket.first()

    override fun record(amount: Double) {
        if (amount > 0) {
            maxBucket.add(amount)
            count.add(1)
            total.add(amount)
            bucket.add(amount)
        }
    }


    /**
     * [0-1]
     */
    override fun percentile(percentile: Double): Double {
        if (!isBucketSorted) {
            bucket.sort()
            isBucketSorted = true
        }
        val index = ceil(percentile * bucket.size).toInt()

        return bucket[index - 1]
//        return nanosToUnitConverter(percentileValue.toLong(), unit)
    }

    override fun histogramCountAtValue(value: Long): Double {
        if (!isBucketSorted) {
            bucket.sort()
            isBucketSorted = true
        }
        var count = 0
        for (values in bucket) {
            if (values <= value) {
                count++
            } else {
                break
            }
        }

        return count.toDouble()
    }

    override suspend fun measure(): Collection<Measurement> =
        listOf(
            MeasurementMetric(count().toDouble(), Statistic.COUNT),
            MeasurementMetric(totalAmount(), Statistic.TOTAL),
            MeasurementMetric(max(), Statistic.MAX),
            MeasurementMetric(mean(), Statistic.MEAN)
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DistributionSummaryImpl
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}