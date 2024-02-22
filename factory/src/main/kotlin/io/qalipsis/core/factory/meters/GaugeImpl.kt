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

import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.Statistic
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.reporter.MeterReporter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

/**
 * Implementation of gauge meter to record instantaneous values.
 */
internal class GaugeImpl(
    override val id: Meter.Id,
    private val meterReporter: MeterReporter,
    private val valueSupplier: Supplier<Number>,
) : Gauge(), Meter.ReportingConfiguration<Gauge> {

    private var reportingConfigured = AtomicBoolean()

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

    override suspend fun measure(): Collection<Measurement> = listOf(MeasurementMetric(value(), Statistic.VALUE))

    override fun value() = valueSupplier.get().toDouble()


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GaugeImpl
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}