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

package io.qalipsis.core.factory.meters

import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.Meter

/**
 * Implementation of [Gauge] that updates the scenario-relevant meter [scenarioMeter], as well as
 * the meter at the global campaign level [globalMeter].
 */
@Suppress("UNCHECKED_CAST")
class CompositeGauge(
    private val scenarioMeter: Gauge,
    private val globalMeter: Gauge,
) : Gauge by scenarioMeter,
    Meter.ReportingConfiguration<Gauge> by (scenarioMeter as Meter.ReportingConfiguration<Gauge>) {

    override fun decrement(): Double {
        globalMeter.decrement()
        return scenarioMeter.decrement()
    }

    override fun decrement(amount: Double): Double {
        globalMeter.decrement(amount)
        return scenarioMeter.decrement(amount)
    }

    override fun increment(): Double {
        globalMeter.increment()
        return scenarioMeter.increment()
    }

    override fun increment(amount: Double): Double {
        globalMeter.increment(amount)
        return scenarioMeter.increment(amount)
    }

}