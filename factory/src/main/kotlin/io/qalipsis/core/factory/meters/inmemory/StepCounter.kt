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

import io.aerisconsulting.catadioptre.KTestable
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
import java.util.concurrent.atomic.DoubleAdder

/**
 * Implementation of meter to record monotonically increasing values, that resets when snapshots are generated.
 *
 * @author Francisca Eze
 */
internal class StepCounter(
    override val id: Meter.Id,
    private val meterReporter: MeterReporter,
) : Counter, Meter.ReportingConfiguration<Counter> {

    private var reportingConfigured = AtomicBoolean()

    @KTestable
    private val currentCount = DoubleAdder()

    override fun report(configure: Meter.ReportingConfiguration<Counter>.() -> Unit): Counter {
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
        toNumber: Counter.() -> Number,
    ) {
        meterReporter.report(this, format, severity, row, column, toNumber)
    }

    override fun count(): Double = currentCount.sum()

    override fun increment() {
        increment(1.0)
    }

    override fun increment(amount: Double) {
        if (amount > 0.0) {
            currentCount.add(amount)
        }
    }

    override suspend fun snapshot(timestamp: Instant): MeterSnapshot =
        MeterSnapshotImpl(timestamp, id.copy(tags = id.tags + ("scope" to "period")), measure())

    override suspend fun summarize(timestamp: Instant) = snapshot(timestamp)

    @KTestable
    private fun measure(): Collection<MeasurementMetric> {
        return listOf(MeasurementMetric(currentCount.sumThenReset(), Statistic.COUNT))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StepCounter
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}