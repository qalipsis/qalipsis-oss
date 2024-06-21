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
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.Statistic
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.reporter.MeterReporter
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.DoubleAdder

/**
 * Implementation of meter to record monotonically increasing values.
 *
 * @author Francisca Eze
 */
internal class CounterImpl(
    override val id: Meter.Id,
    private val meterReporter: MeterReporter,
) : Counter, Meter.ReportingConfiguration<Counter> {

    private var reportingConfigured = AtomicBoolean()

    @KTestable
    private val current = DoubleAdder()

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

    override fun count(): Double = current.sum()

    override fun increment() {
        increment(1.0)
    }

    override fun increment(amount: Double) {
        current.add(amount)
    }

    override suspend fun buildSnapshot(timestamp: Instant): MeterSnapshot<*> =
        MeterSnapshotImpl(timestamp, this, this.measure())

    override suspend fun measure(): Collection<MeasurementMetric> = listOf(MeasurementMetric(count(), Statistic.COUNT))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CounterImpl
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}