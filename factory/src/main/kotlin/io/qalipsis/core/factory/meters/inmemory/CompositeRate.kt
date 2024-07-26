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

import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.Rate

/**
 * Implementation of [Rate] that updates the scenario-relevant meter [scenarioMeter], as well as
 * the meter at the global campaign level [globalMeter].
 */
@Suppress("UNCHECKED_CAST")
class CompositeRate(
    private val scenarioMeter: Rate,
    private val globalMeter: Rate,
) : Rate by scenarioMeter,
    Meter.ReportingConfiguration<Rate> by (scenarioMeter as Meter.ReportingConfiguration<Rate>) {

    override fun incrementTotal(amount: Double) {
        scenarioMeter.incrementTotal(amount)
        globalMeter.incrementTotal(amount)
    }

    override fun decrementTotal(amount: Double) {
        scenarioMeter.decrementTotal(amount)
        globalMeter.decrementTotal(amount)
    }

    override fun incrementBenchmark(amount: Double) {
        scenarioMeter.incrementBenchmark(amount)
        globalMeter.incrementBenchmark(amount)
    }

    override fun decrementBenchmark(amount: Double) {
        scenarioMeter.decrementBenchmark(amount)
        globalMeter.decrementBenchmark(amount)
    }
}