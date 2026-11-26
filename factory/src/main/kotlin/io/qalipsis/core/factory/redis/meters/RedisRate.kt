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
import io.qalipsis.api.meters.Rate
import io.qalipsis.core.reporter.MeterReporter
import java.util.concurrent.atomic.DoubleAdder

/**
 * [Rate] combined in Redis: ratio of the sum of the benchmarks over the sum of the totals of all the factories.
 *
 * @param id identifier of the rate
 * @param levels levels of the rate in Redis: the rate itself, then its levels without zone and without scenario,
 *   when it has these tags (see [RedisMeter.levels])
 * @param meterReporter reporter displaying the meter in the campaign report, when configured with [report]
 */
internal class RedisRate(
    id: Meter.Id,
    levels: List<RedisMeterLevel>,
    meterReporter: MeterReporter,
) : RedisMeter<Rate>(id, levels, meterReporter), Rate {

    /**
     * Local benchmarks and totals, never reset: resetting a [DoubleAdder] loses the concurrent updates.
     */
    private val benchmark = DoubleAdder()

    private val total = DoubleAdder()

    /**
     * Parts of [benchmark] and [total] already drained. Only written by the flush.
     */
    @Volatile
    private var drainedBenchmark = 0.0

    @Volatile
    private var drainedTotal = 0.0

    override fun current(): Double {
        val denominator = level.cumulative(DENOMINATOR_FIELD) + total.sum() - drainedTotal
        return if (denominator == 0.0) 0.0 else
            (level.cumulative(NUMERATOR_FIELD) + benchmark.sum() - drainedBenchmark) / denominator
    }

    override fun incrementTotal(amount: Double) = total.add(amount)

    override fun decrementTotal(amount: Double) = total.add(-amount)

    override fun incrementBenchmark(amount: Double) = benchmark.add(amount)

    override fun decrementBenchmark(amount: Double) = benchmark.add(-amount)

    override fun drainInto(levels: List<RedisMeterLevel>, complete: Boolean) {
        val currentBenchmark = benchmark.sum()
        val currentTotal = total.sum()
        levels.addPending(NUMERATOR_FIELD, currentBenchmark - drainedBenchmark)
        levels.addPending(DENOMINATOR_FIELD, currentTotal - drainedTotal)
        drainedBenchmark = currentBenchmark
        drainedTotal = currentTotal
    }
}
