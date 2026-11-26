/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.core.factory.redis.meters

import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.Timer
import io.qalipsis.core.reporter.MeterReporter
import java.util.concurrent.TimeUnit

/**
 * [Timer] combined in Redis: the durations are recorded in microseconds, like the in-memory timers.
 *
 * @param id identifier of the timer
 * @param levels levels of the timer in Redis: the timer itself, then its levels without zone and without scenario,
 *   when it has these tags (see [RedisMeter.levels])
 * @param meterReporter reporter displaying the meter in the campaign report, when configured with [report]
 * @property percentiles percentiles (0 to 100) to compute from the combined histogram and publish, for example 50.0
 *   and 99.0
 */
internal class RedisTimer(
    id: Meter.Id,
    levels: List<RedisMeterLevel>,
    meterReporter: MeterReporter,
    override val percentiles: Collection<Double>,
) : RedisMeter<Timer>(id, levels, meterReporter), Timer {

    private val distribution = RedisDistribution(level)

    override fun record(amount: Long, unit: TimeUnit?) {
        if (amount >= 0) {
            distribution.record(TimeUnit.MICROSECONDS.convert(amount, unit ?: TimeUnit.NANOSECONDS).toDouble())
        }
    }

    override suspend fun <T> record(block: suspend () -> T): T {
        val start = System.nanoTime()
        return try {
            block()
        } finally {
            record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
        }
    }

    override fun count() = distribution.count()

    override fun totalTime(unit: TimeUnit?) = fromMicros(distribution.total(), unit)

    override fun mean(unit: TimeUnit?) = fromMicros(distribution.mean(), unit)

    override fun max(unit: TimeUnit?) = fromMicros(distribution.max(), unit)

    override fun percentile(percentile: Double, unit: TimeUnit?) = fromMicros(distribution.percentile(percentile), unit)

    override fun drainInto(levels: List<RedisMeterLevel>, complete: Boolean) = distribution.drainInto(levels, complete)

    companion object {

        private val NANOS_IN_MICRO = TimeUnit.MICROSECONDS.toNanos(1).toDouble()

        fun fromMicros(micros: Double, unit: TimeUnit?) =
            if (unit == null || unit == TimeUnit.MICROSECONDS) micros else micros * NANOS_IN_MICRO / unit.toNanos(1)
    }
}
