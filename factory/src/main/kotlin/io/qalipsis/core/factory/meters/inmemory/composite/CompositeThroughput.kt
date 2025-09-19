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

package io.qalipsis.core.factory.meters.inmemory.composite

import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.Throughput
import java.time.Instant

/**
 * Implementation of [Throughput] that updates the scenario-relevant meter [scenarioMeter], as well as
 * the meter at the global campaign level [globalMeter].
 */
@Suppress("UNCHECKED_CAST")
class CompositeThroughput(
    private val scenarioMeter: Throughput,
    private val globalMeter: Throughput,
) : Throughput by scenarioMeter,
    Meter.ReportingConfiguration<Throughput> by (scenarioMeter as Meter.ReportingConfiguration<Throughput>) {

    override suspend fun snapshot(timestamp: Instant): Collection<MeterSnapshot> {
        return scenarioMeter.snapshot(timestamp) + globalMeter.snapshot(timestamp)
    }

    override suspend fun summarize(timestamp: Instant): Collection<MeterSnapshot> {
        return scenarioMeter.summarize(timestamp) + globalMeter.summarize(timestamp)
    }
    
    override fun record(amount: Int) {
        scenarioMeter.record(amount)
        globalMeter.record(amount)
    }

}