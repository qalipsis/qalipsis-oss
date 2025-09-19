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

import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.Rate
import io.qalipsis.api.meters.Statistic
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.factory.meters.MeterSnapshotImpl
import io.qalipsis.core.reporter.MeterReporter
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.DoubleAdder

/**
 * Implementation of [Rate] able to create step-period snapshots, while keeping the overall values over the campaign.
 *
 * It uses a round-robin strategy to avoid reading and writing at the same time.
 *
 * @author Francisca Eze
 */
internal class InMemoryCumulativeRate(
    override val id: Meter.Id,
    private val meterReporter: MeterReporter,
    private val bufferSize: Int = 3,
) : Rate, Meter.ReportingConfiguration<Rate> {

    private var reportingConfigured = AtomicBoolean()

    private val currentBucketHolder = AtomicInteger(0)

    private val buckets = List(bufferSize) { StepRate(id, meterReporter) }.toTypedArray()

    private val total = DoubleAdder()

    override fun report(configure: Meter.ReportingConfiguration<Rate>.() -> Unit): Rate {
        if (reportingConfigured.compareAndSet(false, true)) {
            this.configure()
        }
        return this
    }

    override suspend fun snapshot(timestamp: Instant): Collection<MeterSnapshot> {
        val currentBucket = currentBucketHolder.get()
        // Change to the next available bucket to avoid concurrent changes during the reading.
        return if (currentBucketHolder.compareAndSet(currentBucket, (currentBucket + 1) % bufferSize)) {
            buckets[currentBucket].let { rate ->
                total.add(rate.current())
                rate.snapshot(timestamp)
            }
        } else {
            listOf(MeterSnapshotImpl(timestamp, id, emptyList()))
        }
    }

    override suspend fun summarize(timestamp: Instant): Collection<MeterSnapshot> {
        snapshot(timestamp)
        return listOf(
            MeterSnapshotImpl(
                timestamp,
                id.copy(tags = id.tags + ("scope" to "campaign")),
                listOf(MeasurementMetric(total.sum(), Statistic.VALUE))
            )
        )
    }

    override fun display(
        format: String,
        severity: Number.() -> ReportMessageSeverity,
        row: Short,
        column: Short,
        toNumber: Rate.() -> Number,
    ) {
        meterReporter.report(this, format, severity, row, column, toNumber)
    }

    override fun decrementBenchmark(amount: Double) {
        buckets[currentBucketHolder.get()].decrementBenchmark(amount)
    }

    override fun decrementTotal(amount: Double) {
        buckets[currentBucketHolder.get()].decrementTotal(amount)
    }

    override fun incrementBenchmark(amount: Double) {
        buckets[currentBucketHolder.get()].incrementBenchmark(amount)
    }

    override fun incrementTotal(amount: Double) {
        buckets[currentBucketHolder.get()].incrementTotal(amount)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InMemoryCumulativeRate
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

}