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

package io.qalipsis.core.factory.meters.inmemory.composite

import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.Timer
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Implementation of [Timer] that updates the scenario-relevant meter [scenarioMeter], as well as
 * the meter at the global campaign level [globalMeter].
 */
@Suppress("UNCHECKED_CAST")
class CompositeTimer(
    private val scenarioMeter: Timer,
    private val globalMeter: Timer,
) : Timer by scenarioMeter,
    Meter.ReportingConfiguration<Timer> by (scenarioMeter as Meter.ReportingConfiguration<Timer>) {

    override suspend fun snapshot(timestamp: Instant): Collection<MeterSnapshot> {
        return scenarioMeter.snapshot(timestamp) + globalMeter.snapshot(timestamp)
    }

    override suspend fun summarize(timestamp: Instant): Collection<MeterSnapshot> {
        return scenarioMeter.summarize(timestamp) + globalMeter.summarize(timestamp)
    }

    override suspend fun <T> record(block: suspend () -> T): T {
        val preExecutionTimeInNanos = System.nanoTime()
        return try {
            block()
        } finally {
            val postExecutionTimeInNanos = System.nanoTime()
            record(postExecutionTimeInNanos - preExecutionTimeInNanos, TimeUnit.NANOSECONDS)
        }
    }

    override fun record(amount: Long, unit: TimeUnit?) {
        scenarioMeter.record(amount, unit)
        globalMeter.record(amount, unit)
    }

    override fun record(duration: Duration) {
        scenarioMeter.record(duration)
        globalMeter.record(duration)
    }
}