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

package io.qalipsis.core.factory.meters.inmemory

import com.google.common.util.concurrent.AtomicDouble
import com.tdunning.math.stats.TDigest
import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.Statistic
import io.qalipsis.api.meters.Throughput
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.factory.meters.MeterSnapshotImpl
import io.qalipsis.core.reporter.MeterReporter
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.DoubleAdder

/**
 * Implementation of meter to track the number of hits measured per a configured unit of time.
 *
 * @property timeIncrement the interval of the time slot to calculate the throughput, converted as milliseconds.
 * @property pastBucketHits the throughput of the lately aggregated time slot
 * @property intervalCount the count of time slots that were aggregated so far
 *
 *
 * @author Francisca Eze
 */
internal class StepThroughput(
    override val id: Meter.Id,
    private val meterReporter: MeterReporter,
    override val unit: ChronoUnit,
    override val percentiles: Collection<Double>,
) : Throughput, Meter.ReportingConfiguration<Throughput> {

    private val timeIncrement = Duration.of(1, unit).toMillis()

    @KTestable
    private var pastBucketHits = AtomicDouble()

    @KTestable
    internal var tDigest: TDigest? = supplyIf(percentiles.isNotEmpty()) { TDigest.createMergingDigest(100.0) }

    @KTestable
    private var max = AtomicDouble(0.0)

    internal var intervalCount = 0

    @KTestable
    private val total = AtomicDouble(0.0)

    @KTestable
    internal val aggregatedTotal = AtomicDouble(0.0)

    /**
     * Initializes a fixed-size array of buckets, where each bucket tracks the total number of hits
     * within a specific time slot and reset when the current time slot changes during measurement.
     * This allows for efficient accumulation of hits and periodic resetting to ensure accurate
     * tracking over time.
     */
    @KTestable
    private val buckets = List(BUCKET_COUNT) { DoubleAdder() }.toTypedArray()

    private val alreadyAggregated = AtomicBoolean(false)

    @KTestable
    private var nextTime = AtomicLong(
        Instant.now().truncatedTo(unit).toEpochMilli() + timeIncrement
    )

    private val currentBucketHolder = AtomicInteger(0)

    private var reportingConfigured = AtomicBoolean()

    override fun current(): Double {
        return if (alreadyAggregated.get()) {
            // When a value from a past bucket exists, let's use it.
            pastBucketHits.get()
        } else {
            // Otherwise the current total will be correct.
            total.toDouble()
        }
    }

    override fun mean(): Double {
        return if (alreadyAggregated.get()) {
            // When a value from a past bucket exists, let's use it.
            aggregatedTotal.toDouble() / intervalCount
        } else {
            total.toDouble()
        }
    }

    override fun max(): Double {
        return if (alreadyAggregated.get()) {
            max.toDouble()
        } else {
            total.toDouble()
        }
    }

    override fun total(): Double = total.get()

    override fun percentile(percentile: Double): Double {
        return tDigest?.quantile(percentile / 100) ?: 0.0
    }

    override fun record(amount: Double) {
        total.addAndGet(amount)

        val currentBucket = currentBucketHolder.get()
        if (shouldRotateBucket()) {
            rotateBucket(currentBucket)
        }
        buckets[currentBucketHolder.get()].add(amount)
    }

    /**
     * Rotates the current bucket and resets it.
     */
    private fun rotateBucket(currentBucket: Int) {
        val nextTimeForRotation = nextTime.get()
        if (currentBucketHolder.compareAndSet(currentBucket, (currentBucket + 1) % BUCKET_COUNT)
            && nextTime.compareAndSet(nextTimeForRotation, nextTimeForRotation + timeIncrement)
        ) {
            aggregateAndResetBucket(buckets[currentBucket])
            // When several intervals passed in the meantime but were not aggregated yet, aggregate again.
            val currentBucketIndex = currentBucketHolder.get()
            if (shouldRotateBucket()) {
                rotateBucket(currentBucketIndex)
            }
        }
    }

    /**
     * Checks if the current time has passed the next scheduled rotation time.
     */
    internal fun shouldRotateBucket(): Boolean {
        return Instant.now().toEpochMilli() >= nextTime.toLong()
    }

    /**
     * Aggregates the current bucket and resets it.
     */
    private fun aggregateAndResetBucket(bucket: DoubleAdder) {
        alreadyAggregated.set(true)
        val hits = bucket.sumThenReset()
        pastBucketHits.set(hits)
        aggregatedTotal.addAndGet(hits)
        tDigest?.add(hits)
        intervalCount++

        // Set the new max.
        var curMax: Double
        do {
            curMax = max.get()
            // This do / while loop ensures that the max is only updated if not a higher value was set in the meantime.
        } while (curMax < hits && !max.compareAndSet(curMax, hits))
    }

    override suspend fun snapshot(timestamp: Instant): MeterSnapshot {
        return MeterSnapshotImpl(timestamp, id.copy(tags = id.tags + ("scope" to "period")), measure())
    }

    override suspend fun summarize(timestamp: Instant): MeterSnapshot {
        return snapshot(timestamp)
    }

    @KTestable
    private fun measure(): Collection<Measurement> {
        val result = listOf(
            MeasurementMetric(current(), Statistic.VALUE),
            MeasurementMetric(mean(), Statistic.MEAN),
            MeasurementMetric(max.getAndSet(0.0), Statistic.MAX),
            MeasurementMetric(total.getAndSet(0.0), Statistic.TOTAL)
        ) + (percentiles.map {
            DistributionMeasurementMetric(percentile(it), Statistic.PERCENTILE, it)
        })
        tDigest?.let {
            synchronized(it) {
                tDigest = TDigest.createMergingDigest(100.0)
            }
        }
        alreadyAggregated.set(false)
        return result
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StepThroughput
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun report(configure: Meter.ReportingConfiguration<Throughput>.() -> Unit): Throughput {
        if (!reportingConfigured.compareAndExchange(false, true)) {
            this.configure()
        }
        return this
    }

    companion object TimeHelper {

        private const val BUCKET_COUNT = 3
    }
}