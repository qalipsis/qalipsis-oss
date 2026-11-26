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

import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Meter
import io.qalipsis.core.reporter.MeterReporter
import java.util.concurrent.atomic.DoubleAdder

/**
 * [Counter] combined in Redis: sum of the increments of all the factories.
 *
 * @param id identifier of the counter
 * @param levels levels of the counter in Redis: the counter itself, then its levels without zone and without scenario,
 *   when it has these tags (see [RedisMeter.levels])
 * @param meterReporter reporter displaying the meter in the campaign report, when configured with [report]
 */
internal class RedisCounter(
    id: Meter.Id,
    levels: List<RedisMeterLevel>,
    meterReporter: MeterReporter,
) : RedisMeter<Counter>(id, levels, meterReporter), Counter {

    /**
     * Local increments, never reset: resetting a [DoubleAdder] loses the concurrent increments.
     */
    private val local = DoubleAdder()

    /**
     * Part of [local] already drained. Only written by the flush.
     */
    @Volatile
    private var drained = 0.0

    override fun increment() = increment(1.0)

    override fun increment(amount: Double) {
        if (amount > 0.0) {
            local.add(amount)
        }
    }

    override fun count() = level.cumulative(COUNT_FIELD) + local.sum() - drained

    override fun drainInto(levels: List<RedisMeterLevel>, complete: Boolean) {
        val current = local.sum()
        levels.addPending(COUNT_FIELD, current - drained)
        drained = current
    }
}
