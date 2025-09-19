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

import com.google.common.util.concurrent.AtomicDouble
import com.tdunning.math.stats.TDigest
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.Statistic
import io.qalipsis.api.meters.Throughput
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.factory.meters.MeterSnapshotImpl
import io.qalipsis.core.reporter.MeterReporter
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.DoubleAdder

/**
 * Implementation of [Throughput] able to create step-period snapshots, while keeping the overall values over the campaign.
 *
 * It uses a round-robin strategy to avoid reading and writing at the same time.
 *
 * @author Francisca Eze
 */
internal class InMemoryCumulativeThroughput(
    override val id: Meter.Id,
    private val meterReporter: MeterReporter,
    override val unit: ChronoUnit,
    override val percentiles: Collection<Double>,
) : Throughput, Meter.ReportingConfiguration<Throughput> {

    private var reportingConfigured = AtomicBoolean()

    private val total = DoubleAdder()

    private val aggregatedTotal = AtomicDouble(0.0)

    private var tDigest: TDigest? = supplyIf(percentiles.isNotEmpty()) { TDigest.createMergingDigest(100.0) }

    private val max = AtomicDouble(0.0)

    private val current = AtomicDouble(0.0)

    private var intervalCount = 0

    private val stepThroughput = StepThroughput(id, meterReporter, unit, percentiles)

    override fun total(): Double {
        return stepThroughput.total()
    }

    override fun current(): Double {
        return current.get()
    }

    override fun max(): Double = stepThroughput.max()

    override fun mean(): Double {
        return if (intervalCount == 0) {
            total()
        } else {
            aggregatedTotal.toDouble() / intervalCount
        }
    }

    override fun percentile(percentile: Double): Double {
        return tDigest?.quantile(percentile / 100) ?: 0.0
    }

    override fun record(amount: Double) {
        if (amount > 0) {
            val shouldRotateBucket = stepThroughput.shouldRotateBucket()
            stepThroughput.record(amount)
            if (shouldRotateBucket) {
                intervalCount = stepThroughput.intervalCount
                aggregatedTotal.set(stepThroughput.aggregatedTotal.toDouble())
                current.set(stepThroughput.current())

                tDigest?.add(stepThroughput.tDigest)
            }
        }
    }

    override suspend fun snapshot(timestamp: Instant): Collection<MeterSnapshot> {
        total.add(stepThroughput.total())
        max.set(max.get().coerceAtLeast(stepThroughput.max()))
        aggregatedTotal.set(stepThroughput.aggregatedTotal.toDouble())

        return stepThroughput.snapshot(timestamp)
    }

    override suspend fun summarize(timestamp: Instant): Collection<MeterSnapshot> {
        snapshot(timestamp)
        val measures = listOf(
            MeasurementMetric(current(), Statistic.VALUE),
            MeasurementMetric(mean(), Statistic.MEAN),
            MeasurementMetric(max.get(), Statistic.MAX),
            MeasurementMetric(total.sum(), Statistic.TOTAL),
        ) + (percentiles.map {
            DistributionMeasurementMetric(percentile(it), Statistic.PERCENTILE, it)
        })
        return listOf(MeterSnapshotImpl(timestamp, id.copy(tags = id.tags + ("scope" to "campaign")), measures))
    }

    override fun display(
        format: String,
        severity: Number.() -> ReportMessageSeverity,
        row: Short,
        column: Short,
        toNumber: Throughput.() -> Number,
    ) {
        meterReporter.report(this, format, severity, row, column, toNumber)
    }

    override fun report(configure: Meter.ReportingConfiguration<Throughput>.() -> Unit): Throughput {
        if (!reportingConfigured.compareAndExchange(false, true)) {
            this.configure()
        }
        return this
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InMemoryCumulativeThroughput
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}