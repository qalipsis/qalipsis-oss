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

package io.qalipsis.core.reporter

import io.micronaut.context.annotation.Primary
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.report.ReportMessageSeverity
import jakarta.inject.Singleton

@Singleton
@Primary
internal class CompositeMeterReporter(private val meterReports: Collection<MeterReporter>) : MeterReporter {

    override fun <T : Meter<*>> report(
        meter: T,
        format: String,
        severity: Number.() -> ReportMessageSeverity,
        row: Short,
        column: Short,
        toNumber: T.() -> Number
    ) {
        meterReports.forEach {
            it.report(meter, format, severity, row, column, toNumber)
        }
    }

    override fun clean() {
        meterReports.forEach {
            it.clean()
        }
    }
}