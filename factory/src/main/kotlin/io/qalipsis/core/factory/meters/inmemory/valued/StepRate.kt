/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.meters.Measurement
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
import java.util.concurrent.atomic.DoubleAdder

/**
 * Implementation of meter to record the ratio between two independently tracked measurements.
 * It resets when snapshots are generated.
 *
 * @author Francisca Eze
 */
internal class StepRate(
    override val id: Meter.Id,
    private val meterReporter: MeterReporter,
) : Rate, Meter.ReportingConfiguration<Rate> {

    @KTestable
    private val total = DoubleAdder()

    @KTestable
    private val benchmark = DoubleAdder()

    private var reportingConfigured = AtomicBoolean()

    override fun current(): Double {
        return if (total.sum() == 0.0) 0.0 else {
            benchmark.sum() / total.sum()
        }
    }

    @KTestable
    private fun measure(): Collection<Measurement> {
        return if (total.sum() == 0.0) listOf(MeasurementMetric(
            0.0,
            Statistic.VALUE
        )) else listOf(
            MeasurementMetric(
                benchmark.sumThenReset() / total.sumThenReset(),
                Statistic.VALUE
            )
        )
    }

    override suspend fun snapshot(timestamp: Instant): Collection<MeterSnapshot> =
        listOf(MeterSnapshotImpl(timestamp, id.copy(tags = id.tags + ("scope" to "period")), this.measure()))

    override suspend fun summarize(timestamp: Instant) =
        snapshot(timestamp).map { it.duplicate(it.meterId.copy(tags = it.meterId.tags + ("scope" to "campaign"))) }

    override fun incrementTotal(amount: Double) {
        total.add(amount)
    }

    override fun decrementTotal(amount: Double) {
        total.add(-amount)
    }

    override fun incrementBenchmark(amount: Double) {
        benchmark.add(amount)
    }

    override fun decrementBenchmark(amount: Double) {
        benchmark.add(-amount)
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StepRate
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun report(configure: Meter.ReportingConfiguration<Rate>.() -> Unit): Rate {
        if (!reportingConfigured.compareAndExchange(false, true)) {
            this.configure()
        }
        return this
    }
}