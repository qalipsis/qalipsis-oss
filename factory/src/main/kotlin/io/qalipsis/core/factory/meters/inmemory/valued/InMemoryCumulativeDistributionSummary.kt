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
import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.Statistic
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.factory.meters.MeterSnapshotImpl
import io.qalipsis.core.reporter.MeterReporter
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.DoubleAdder
import java.util.concurrent.atomic.LongAdder

/**
 * Implementation of [DistributionSummary] able to create step-period snapshots, while keeping the overall values over the campaign.
 *
 * It uses a round-robin strategy to avoid reading and writing at the same time.
 *
 * @author Eric Jessé
 */
internal class InMemoryCumulativeDistributionSummary(
    override val id: Meter.Id,
    private val meterReporter: MeterReporter,
    override val percentiles: Collection<Double>,
    private val bufferSize: Int = 3,
) : DistributionSummary, Meter.ReportingConfiguration<DistributionSummary> {

    private var reportingConfigured = AtomicBoolean()

    private val currentBucketHolder = AtomicInteger(0)

    private val buckets = List(bufferSize) { StepDistributionSummary(id, meterReporter, percentiles) }.toTypedArray()

    private val counter = LongAdder()

    private val total = DoubleAdder()

    private var tDigest: TDigest? = supplyIf(percentiles.isNotEmpty()) { TDigest.createMergingDigest(100.0) }

    private val max = AtomicDouble(0.0)

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

    override fun count(): Long = counter.toLong() + buckets[currentBucketHolder.get()].count()

    override fun totalAmount(): Double = total.toDouble() + buckets[currentBucketHolder.get()].totalAmount()

    override fun max(): Double = max.get().coerceAtLeast(buckets[currentBucketHolder.get()].max())

    override fun mean(): Double {
        val count = count()
        return if (count == 0L) 0.0 else totalAmount() / count
    }

    override fun record(amount: Double) {
        if (amount > 0) {
            buckets[currentBucketHolder.get()].record(amount)
            tDigest?.let {
                synchronized(it) {
                    it.add(amount)
                }
            }
        }
    }

    override suspend fun snapshot(timestamp: Instant): Collection<MeterSnapshot> {
        val currentBucket = currentBucketHolder.get()
        // Change to the next available bucket to avoid concurrent changes during the reading.
        return if (currentBucketHolder.compareAndSet(currentBucket, (currentBucket + 1) % bufferSize)) {
            buckets[currentBucket].let { summary ->
                counter.add(summary.count())
                total.add(summary.totalAmount())
                max.set(max.get().coerceAtLeast(summary.max()))
                summary.snapshot(timestamp)
            }
        } else {
            listOf(MeterSnapshotImpl(timestamp, id, emptyList()))
        }
    }

    override suspend fun summarize(timestamp: Instant): Collection<MeterSnapshot> {
        snapshot(timestamp)
        val measures = listOf(
            MeasurementMetric(mean(), Statistic.MEAN),
            MeasurementMetric(counter.sum().toDouble(), Statistic.COUNT),
            MeasurementMetric(total.sum(), Statistic.TOTAL),
            MeasurementMetric(max.get(), Statistic.MAX),
        ) + (percentiles.map {
            DistributionMeasurementMetric(percentile(it), Statistic.PERCENTILE, it)
        })

        return listOf(MeterSnapshotImpl(timestamp, id.copy(tags = id.tags + ("scope" to "campaign")), measures))
    }

    override fun percentile(percentile: Double): Double {
        return tDigest?.quantile(percentile / 100) ?: 0.0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InMemoryCumulativeDistributionSummary
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}