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

import com.google.common.util.concurrent.AtomicDouble
import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.Meter
import io.qalipsis.core.reporter.MeterReporter
import java.util.concurrent.atomic.DoubleAdder

/**
 * [Gauge] combined in Redis: sum of the values of all the factories.
 *
 * The local value is flushed as the delta since the latest flush, so that Redis maintains the sum without knowing
 * the value of each factory.
 *
 * @param id identifier of the gauge
 * @param levels levels of the gauge in Redis: the gauge itself, then its levels without zone and without scenario,
 *   when it has these tags (see [RedisMeter.levels])
 * @param meterReporter reporter displaying the meter in the campaign report, when configured with [report]
 */
internal class RedisGauge(
    id: Meter.Id,
    levels: List<RedisMeterLevel>,
    meterReporter: MeterReporter,
) : RedisMeter<Gauge>(id, levels, meterReporter), Gauge {

    /**
     * Latest value set locally, from which the increments are counted separately to avoid the contention.
     */
    private val base = AtomicDouble(0.0)

    private val increments = DoubleAdder()

    private val local: Double
        get() = base.get() + increments.sum()

    /**
     * Local value already sent to Redis. Only accessed by the flush.
     */
    @Volatile
    private var flushed = 0.0

    override fun value() = level.cumulative(VALUE_FIELD) + local - flushed

    override fun set(value: Double): Double {
        base.set(value - increments.sum())
        return value
    }

    override fun increment() = increment(1.0)

    override fun increment(amount: Double): Double {
        increments.add(amount)
        return local
    }

    override fun decrement() = decrement(1.0)

    override fun decrement(amount: Double) = increment(-amount)

    override fun drainInto(levels: List<RedisMeterLevel>, complete: Boolean) {
        val current = local
        val delta = current - flushed
        flushed = current
        levels.addPending(VALUE_FIELD, delta)
    }
}
