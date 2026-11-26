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
import io.qalipsis.api.meters.Throughput
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.BUCKETS_FIELD
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.FOLDED_HITS_FIELD
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.FOLDED_MAX_FIELD
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.HITS_FIELD
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.INTERVALS_FIELD
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.TOTAL_FIELD
import io.qalipsis.core.reporter.MeterReporter
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.DoubleAdder

/**
 * [Throughput] combined in Redis: the hits of all the factories are summed by interval of [unit].
 *
 * Redis keeps the hits of the recent intervals and folds the older ones into a count of intervals, a sum, a max and
 * a histogram, so that the size of the stored values does not grow with the duration of the campaign.
 *
 * @param id identifier of the throughput
 * @param levels levels of the throughput in Redis: the throughput itself, then its levels without zone and without scenario,
 *   when it has these tags (see [RedisMeter.levels])
 * @param meterReporter reporter displaying the meter in the campaign report, when configured with [report]
 * @property unit duration of the intervals counting the hits: with [ChronoUnit.SECONDS], [current] returns the hits of
 *   the latest completed second, summed over all the factories, and the other statistics are computed over the
 *   completed seconds
 * @property percentiles percentiles (0 to 100) of the hits per interval to compute and publish, for example 50.0 and
 *   99.0
 */
internal class RedisThroughput(
    id: Meter.Id,
    levels: List<RedisMeterLevel>,
    meterReporter: MeterReporter,
    override val unit: ChronoUnit,
    override val percentiles: Collection<Double>,
) : RedisMeter<Throughput>(id, levels, meterReporter), Throughput {

    private val intervalMillis = Duration.of(1, unit).toMillis()

    private val total = DoubleAdder()

    @Volatile
    private var drainedTotal = 0.0

    /**
     * Local hits by start of interval, never reset to not lose concurrent records.
     */
    private val hits = ConcurrentHashMap<Long, DoubleAdder>()

    private val drainedHits = ConcurrentHashMap<Long, Double>()

    /**
     * Hits of the current interval, cached to avoid a lookup at each record.
     */
    @Volatile
    private var currentInterval = CurrentInterval(Long.MIN_VALUE, Long.MIN_VALUE, DoubleAdder())

    override fun record(amount: Double) {
        total.add(amount)
        val now = System.currentTimeMillis()
        var interval = currentInterval
        if (now >= interval.end || now < interval.start) {
            val start = now - now % intervalMillis
            interval = CurrentInterval(start, start + intervalMillis, hits.computeIfAbsent(start) { DoubleAdder() })
            currentInterval = interval
        }
        interval.hits.add(amount)
    }

    private class CurrentInterval(val start: Long, val end: Long, val hits: DoubleAdder)

    /**
     * Statistics computed from the combined values [cumulative] and the intervals completed before [completedUntil].
     */
    private class CachedStats(val cumulative: Map<String, Any>, val completedUntil: Long, val stats: Stats)

    /**
     * Latest statistics, only recomputed when an interval completes or the combined values are refreshed by a flush.
     */
    @Volatile
    private var cachedStats: CachedStats? = null

    override fun total() = level.cumulative(TOTAL_FIELD) + total.sum() - drainedTotal

    override fun current() = stats().current

    override fun mean() = stats().mean

    override fun max() = stats().max

    override fun percentile(percentile: Double) = stats().percentile(percentile)

    /**
     * Statistics of the completed intervals, from the cache when neither an interval completed nor the combined
     * values changed since they were computed: the hits of a completed interval no longer change locally.
     */
    private fun stats(): Stats {
        val now = System.currentTimeMillis()
        val completedUntil = now - now % intervalMillis
        val cumulative = level.cumulative
        return cachedStats?.takeIf { it.cumulative === cumulative && it.completedUntil == completedUntil }?.stats
            ?: computeStats(cumulative, completedUntil).also { cachedStats = CachedStats(cumulative, completedUntil, it) }
    }

    /**
     * Computes the statistics of the intervals completed before [completedUntil], from the [cumulative] values
     * combined in Redis and the local hits not flushed yet.
     */
    @Suppress("UNCHECKED_CAST")
    private fun computeStats(cumulative: Map<String, Any>, completedUntil: Long): Stats {
        val undrained = hits.mapNotNull { (interval, adder) ->
            (adder.sum() - (drainedHits[interval] ?: 0.0)).takeIf { it > 0 }?.let { interval.toString() to it }
        }.toMap()
        val cumulativeHits = cumulative[HITS_FIELD] as Map<String, Double>? ?: emptyMap()
        return stats(cumulative + (HITS_FIELD to LogHistogram.merge(cumulativeHits, undrained)), intervalMillis, completedUntil)
    }

    override fun drainInto(levels: List<RedisMeterLevel>, complete: Boolean) {
        val currentTotal = total.sum()
        val now = System.currentTimeMillis()
        val drained = hits.mapNotNull { (interval, adder) ->
            val current = adder.sum()
            val delta = current - (drainedHits.put(interval, current) ?: 0.0)
            // The old intervals do not receive hits anymore.
            if (interval < now - RETAINED_INTERVALS * intervalMillis) {
                hits.remove(interval)
                drainedHits.remove(interval)
            }
            if (delta > 0) interval.toString() to delta else null
        }.toMap()
        levels.forEach { level ->
            level.addPending(TOTAL_FIELD, currentTotal - drainedTotal)
            level.addPending(HITS_FIELD, drained)
        }
        drainedTotal = currentTotal
    }

    /**
     * Statistics of the hits by interval.
     */
    class Stats(val current: Double, val mean: Double, val max: Double, private val buckets: Map<String, Double>) {

        fun percentile(percentile: Double) = LogHistogram.percentile(buckets, percentile)
    }

    companion object {

        private const val RETAINED_INTERVALS = 3

        /**
         * Computes the statistics from the [values] stored in Redis, considering only the intervals completed at
         * [now], or all of them when [now] is null.
         */
        @Suppress("UNCHECKED_CAST")
        fun stats(values: Map<String, Any>, intervalMillis: Long, now: Long?): Stats {
            fun number(field: String) = (values[field] as Number?)?.toDouble() ?: 0.0
            val completed = (values[HITS_FIELD] as Map<String, Double>? ?: emptyMap())
                .filterKeys { now == null || it.toLong() + intervalMillis <= now }
            val intervals = number(INTERVALS_FIELD) + completed.size
            val sum = number(FOLDED_HITS_FIELD) + completed.values.sum()
            val buckets = LogHistogram.merge(
                values[BUCKETS_FIELD] as Map<String, Double>? ?: emptyMap(),
                completed.values.groupingBy { LogHistogram.bucketOf(it) }.fold(0.0) { count, _ -> count + 1 }
            )
            return Stats(
                current = completed.maxByOrNull { it.key.toLong() }?.value ?: 0.0,
                mean = if (intervals == 0.0) 0.0 else sum / intervals,
                max = maxOf(number(FOLDED_MAX_FIELD), completed.values.maxOrNull() ?: 0.0),
                buckets = buckets
            )
        }
    }
}
