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
import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.Statistic
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.factory.meters.MeterSnapshotImpl
import io.qalipsis.core.reporter.MeterReporter
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementation of gauge meter to record instantaneous values.
 *
 * @author Francisca Eze
 */
internal class InMemoryGauge(
    override val id: Meter.Id,
    private val meterReporter: MeterReporter
) : Gauge, Meter.ReportingConfiguration<Gauge> {

    private var reportingConfigured = AtomicBoolean()

    @KTestable
    private val current = AtomicDouble(0.0)

    override fun value(): Double = current.get()

    @KTestable
    private fun measure(): Collection<Measurement> = listOf(MeasurementMetric(value(), Statistic.VALUE))

    override suspend fun snapshot(timestamp: Instant): Collection<MeterSnapshot> =
        listOf(MeterSnapshotImpl(timestamp, id.copy(tags = id.tags + ("scope" to "period")), measure()))

    override suspend fun summarize(timestamp: Instant): Collection<MeterSnapshot> =
        listOf(MeterSnapshotImpl(timestamp, id.copy(tags = id.tags + ("scope" to "campaign")), measure()))

    override fun increment(): Double {
        return increment(1.0)
    }

    override fun increment(amount: Double): Double {
        return current.addAndGet(amount)
    }

    override fun decrement(): Double {
        return decrement(1.0)
    }

    override fun decrement(amount: Double): Double {
        return current.addAndGet(-amount)
    }

    override fun report(configure: Meter.ReportingConfiguration<Gauge>.() -> Unit): Gauge {
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
        toNumber: Gauge.() -> Number,
    ) {
        meterReporter.report(this, format, severity, row, column, toNumber)
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InMemoryGauge
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}