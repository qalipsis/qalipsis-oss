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

import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.Meter

/**
 * Implementation of [DistributionSummary] that updates the scenario-relevant meter [scenarioMeter], as well as
 * the meter at the global campaign level [globalMeter].
 */
@Suppress("UNCHECKED_CAST")
class CompositeDistributionSummary(
    private val scenarioMeter: DistributionSummary,
    private val globalMeter: DistributionSummary,
) : DistributionSummary by scenarioMeter,
    Meter.ReportingConfiguration<DistributionSummary> by (scenarioMeter as Meter.ReportingConfiguration<DistributionSummary>) {

    override fun record(amount: Double) {
        scenarioMeter.record(amount)
        globalMeter.record(amount)
    }
}