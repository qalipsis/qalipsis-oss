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

import io.qalipsis.api.meters.Counter
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

/**
 * Implementation of [Counter] able to create step-period snapshots, while keeping the overall values over the campaign.
 *
 * It uses a round-robin strategy to avoid reading and writing at the same time.
 *
 * @author Eric Jessé
 */
internal class InMemoryCumulativeCounter(
    override val id: Meter.Id,
    private val meterReporter: MeterReporter,
    private val bufferSize: Int = 3,
) : Counter, Meter.ReportingConfiguration<Counter> {

    private var reportingConfigured = AtomicBoolean()

    private val currentBucketHolder = AtomicInteger(0)

    private val buckets = List(bufferSize) { StepCounter(id, meterReporter) }.toTypedArray()

    private val total = DoubleAdder()

    override fun report(configure: Meter.ReportingConfiguration<Counter>.() -> Unit): Counter {
        if (reportingConfigured.compareAndSet(false, true)) {
            this.configure()
        }
        return this
    }

    override suspend fun snapshot(timestamp: Instant): MeterSnapshot {
        val currentBucket = currentBucketHolder.get()
        // Change to the next available bucket to avoid concurrent changes during the reading.
        return if (currentBucketHolder.compareAndSet(currentBucket, (currentBucket + 1) % bufferSize)) {
            buckets[currentBucket].let { counter ->
                total.add(counter.count())
                counter.snapshot(timestamp)
            }
        } else {
            MeterSnapshotImpl(timestamp, id, emptyList())
        }
    }

    override suspend fun summarize(timestamp: Instant): MeterSnapshot {
        snapshot(timestamp)
        return MeterSnapshotImpl(
            timestamp,
            id.copy(tags = id.tags + ("scope" to "campaign")),
            listOf(MeasurementMetric(total.sum(), Statistic.COUNT))
        )
    }

    override fun display(
        format: String,
        severity: Number.() -> ReportMessageSeverity,
        row: Short,
        column: Short,
        toNumber: Counter.() -> Number,
    ) {
        meterReporter.report(this, format, severity, row, column, toNumber)
    }

    override fun increment() {
        buckets[currentBucketHolder.get()].increment()
    }

    override fun increment(amount: Double) {
        buckets[currentBucketHolder.get()].increment(amount)
    }

    override fun count(): Double = total.toDouble() + buckets[currentBucketHolder.get()].count()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InMemoryCumulativeCounter
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}