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
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.BUCKETS_FIELD
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.COUNT_FIELD
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.MAX_FIELD
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.TOTAL_FIELD
import java.util.concurrent.atomic.DoubleAdder
import java.util.concurrent.atomic.LongAdder

/**
 * Local recorder of a distribution (count, total, max and histogram), shared by the timers and distribution summaries.
 *
 * The values are never reset, in order to not lose concurrent records: the flush drains the part recorded since the
 * previous drain.
 *
 * @param level level whose cumulative combined values are added to the local ones when reading, the meter itself
 */
internal class RedisDistribution(private val level: RedisMeterLevel) {

    private val count = LongAdder()

    private val total = DoubleAdder()

    private val max = AtomicDouble(Double.NEGATIVE_INFINITY)

    private val histogram = LogHistogram.Recorder()

    @Volatile
    private var drainedCount = 0L

    @Volatile
    private var drainedTotal = 0.0

    fun record(value: Double) {
        count.increment()
        total.add(value)
        var currentMax = max.get()
        while (value > currentMax && !max.compareAndSet(currentMax, value)) {
            currentMax = max.get()
        }
        histogram.record(value)
    }

    fun count(): Long = level.cumulative(COUNT_FIELD).toLong() + count.sum() - drainedCount

    fun total(): Double = level.cumulative(TOTAL_FIELD) + total.sum() - drainedTotal

    fun mean(): Double = count().let { if (it == 0L) 0.0 else total() / it }

    fun max(): Double = maxOf(level.cumulative(MAX_FIELD), max.get().takeIf { it.isFinite() } ?: 0.0)

    fun percentile(percentile: Double): Double =
        LogHistogram.percentile(LogHistogram.merge(level.cumulativeMap(BUCKETS_FIELD), histogram.undrained()), percentile)

    /**
     * Drains the count, total and max, and the histogram only when [complete] is true: it is the heaviest part to
     * combine in Redis and is only needed for the percentiles.
     */
    fun drainInto(levels: List<RedisMeterLevel>, complete: Boolean) {
        val currentCount = count.sum()
        val currentTotal = total.sum()
        val currentMax = max.getAndSet(Double.NEGATIVE_INFINITY)
        val buckets = if (complete) histogram.drain() else emptyMap()
        levels.forEach { level ->
            level.addPending(COUNT_FIELD, (currentCount - drainedCount).toDouble())
            level.addPending(TOTAL_FIELD, currentTotal - drainedTotal)
            level.addPending(MAX_FIELD, currentMax)
            level.addPending(BUCKETS_FIELD, buckets)
        }
        drainedCount = currentCount
        drainedTotal = currentTotal
    }
}
