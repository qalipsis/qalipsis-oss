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

import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.atomic.AtomicReferenceArray
import java.util.concurrent.atomic.LongAdder
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow

/**
 * Logarithmic histogram with a relative accuracy of [RELATIVE_ACCURACY] on the percentiles, mergeable by simply
 * adding the counts of its buckets, which makes it suitable to combine the distributions of several factories in Redis.
 *
 * ## Buckets
 *
 * The strictly positive values are counted in buckets whose bounds grow geometrically by a factor
 * `γ = (1 + α) / (1 - α)`, where `α` is [RELATIVE_ACCURACY]: with `α = 1 %`, `γ ≈ 1.0202`. The bucket of index `i`
 * counts the values in `(γ^(i-1), γ^i]`, so `i = ceil(ln(value) / ln(γ))`. Each bucket is represented by the value
 * `2 γ^i / (γ + 1)`, which is at most `α` away (relatively) from any value of the bucket. The values lower than or
 * equal to 0 are counted in [ZERO_BUCKET], represented by 0.
 *
 * | Value  | Bucket | Bounds of the bucket   | Representative value | Relative error |
 * |--------|--------|------------------------|----------------------|----------------|
 * | 0.5    | -34    | (0.4966, 0.5066]       | 0.5015               | +0.31 %        |
 * | 1      | 0      | (0.9802, 1.0000]       | 0.9900               | -1.00 %        |
 * | 2      | 35     | (1.9739, 2.0138]       | 1.9937               | -0.32 %        |
 * | 100    | 231    | (99.4996, 101.5097]    | 100.4946             | +0.49 %        |
 * | 1000   | 346    | (992.5030, 1012.5535]  | 1002.4280            | +0.24 %        |
 * | 60000  | 551    | (59896.10, 61106.12]   | 60495.06             | +0.83 %        |
 *
 * The width of the buckets is proportional to their values, so that the count of buckets only grows with the
 * logarithm of the range: about 115 buckets per order of magnitude, whatever the unit of the values.
 *
 * ## Storage and merge
 *
 * A histogram is a map of the bucket index, as a string to be stored as a field of a JSON object in Redis, to the
 * count of values. Two histograms are merged by adding the counts of the same buckets, in any order and without
 * losing accuracy, in Kotlin with [merge] as in the Lua scripts:
 *
 * - factory A recorded 2 and 2: `{"35": 2}`,
 * - factory B recorded 2 and 100: `{"35": 1, "231": 1}`,
 * - combined: `{"35": 3, "231": 1}`.
 *
 * ## Percentiles
 *
 * The [percentile] `p` is the representative value of the bucket containing the value of rank `ceil(p / 100 × count)`,
 * the buckets being sorted by index. With the values 1, 2, 2 and 100, i.e. `{"0": 1, "35": 2, "231": 1}`:
 *
 * - the median (rank 2) is in the bucket 35: 1.9937,
 * - the 99th percentile (rank 4) is in the bucket 231: 100.4946.
 */
internal object LogHistogram {

    const val RELATIVE_ACCURACY = 0.01

    /**
     * Ratio between the upper and lower bounds of a bucket, about 1.0202.
     */
    private const val GAMMA = (1 + RELATIVE_ACCURACY) / (1 - RELATIVE_ACCURACY)

    /**
     * Natural logarithm of [GAMMA], about 0.02: the width of a bucket in the logarithmic scale.
     */
    val LOG_GAMMA = ln(GAMMA)

    /**
     * Bucket of the values lower than or equal to 0.
     */
    const val ZERO_BUCKET = "z"

    /**
     * Identifier of the bucket of [value], as stored in Redis: for example `"35"` for 2, [ZERO_BUCKET] for 0 or -3.
     */
    fun bucketOf(value: Double): String = if (value <= 0.0) ZERO_BUCKET else indexOf(value).toString()

    /**
     * Value representing all the values of [bucket]: `2 γ^i / (γ + 1)` for the bucket `i`, 0 for [ZERO_BUCKET].
     *
     * This value lies between the bounds `γ^(i-1)` and `γ^i` of the bucket, at the same relative distance `α` from
     * both: for example 1.9937 for the bucket 35, whose bounds are 1.9739 and 2.0138.
     */
    private fun valueOf(bucket: String): Double =
        if (bucket == ZERO_BUCKET) 0.0 else 2 * GAMMA.pow(bucket.toDouble()) / (GAMMA + 1)

    /**
     * Estimates the [percentile] (0 to 100) of the values counted in [buckets], 0 when empty.
     *
     * The buckets are sorted by index, [ZERO_BUCKET] first, and their counts cumulated until reaching the rank of the
     * percentile, at least 1 and at most the total count.
     */
    fun percentile(buckets: Map<String, Double>, percentile: Double): Double {
        val sorted = buckets.entries.filter { it.value > 0 }
            .sortedBy { if (it.key == ZERO_BUCKET) Double.NEGATIVE_INFINITY else it.key.toDouble() }
        val count = sorted.sumOf { it.value }
        if (count == 0.0) {
            return 0.0
        }
        val rank = ceil(percentile / 100 * count).coerceIn(1.0, count)
        var cumulated = 0.0
        return valueOf(sorted.first { cumulated += it.value; cumulated >= rank }.key)
    }

    /**
     * Merges [other] into a copy of [buckets], adding the counts of the common buckets: `{"35": 2}` merged with
     * `{"35": 1, "231": 1}` gives `{"35": 3, "231": 1}`. Returns [buckets] itself when [other] is empty.
     */
    fun merge(buckets: Map<String, Double>, other: Map<String, Double>): Map<String, Double> =
        if (other.isEmpty()) buckets else HashMap(buckets).apply { other.forEach { (bucket, count) -> merge(bucket, count, Double::plus) } }

    /**
     * Index of the bucket of a strictly positive value, `ceil(ln(value) / ln(γ))`: -34 for 0.5, 0 for 1, 35 for 2,
     * 231 for 100.
     */
    fun indexOf(value: Double): Int {
        // Faster than Math.ceil, which is not intrinsic on all the JVMs.
        val position = ln(value) / LOG_GAMMA
        val truncated = position.toInt()
        return if (position > truncated) truncated + 1 else truncated
    }

    /**
     * Thread-safe local recorder of the buckets, never reset: [drain] returns the counts recorded since the latest call.
     *
     * The buckets are kept in an array indexed by bucket, allocated lazily, so that recording a value allocates
     * nothing once its bucket exists. The slot of the bucket `i` is `i - MIN_INDEX`: the bucket -1036 (1e-9) is in
     * the slot 0, the bucket 35 (2) in the slot 1071. The values out of the range are counted in the first or last
     * slot.
     *
     * For example, after recording 2, 2 and 100, [drain] returns `{"35": 2, "231": 1}`; after recording 2 again, the
     * next [drain] returns `{"35": 1}` only.
     */
    class Recorder {

        /**
         * Counts of the values by slot of bucket, created at the first value of the bucket.
         */
        private val counts = AtomicReferenceArray<LongAdder>(BUCKETS_COUNT)

        /**
         * Count of the values lower than or equal to 0, or NaN.
         */
        private val zeros = LongAdder()

        /**
         * Counts already drained, only written by the flush.
         */
        private val drained = AtomicLongArray(BUCKETS_COUNT)

        /**
         * Count of [zeros] already drained, only written by the flush.
         */
        @Volatile
        private var drainedZeros = 0L

        /**
         * Counts [value] in its bucket.
         */
        fun record(value: Double) {
            if (value <= 0.0 || value.isNaN()) {
                zeros.increment()
            } else {
                val slot = (indexOf(value) - MIN_INDEX).coerceIn(0, BUCKETS_COUNT - 1)
                (counts[slot] ?: LongAdder().let { if (counts.compareAndSet(slot, null, it)) it else counts[slot] }).increment()
            }
        }

        /**
         * Counts recorded and not yet drained.
         */
        fun undrained(): Map<String, Double> = collect(drain = false)

        /**
         * Counts recorded since the latest call, which are then considered as drained.
         */
        fun drain(): Map<String, Double> = collect(drain = true)

        /**
         * Counts by bucket recorded since the latest drain, only containing the buckets having new values. When
         * [drain] is true, the current counts are kept as drained, so that the next call only returns the newer values.
         *
         * The counts are read without stopping the concurrent records: a value recorded during the collection is
         * either returned now or by the next call, but never lost nor counted twice.
         */
        private fun collect(drain: Boolean): Map<String, Double> {
            val result = HashMap<String, Double>()
            val currentZeros = zeros.sum()
            if (currentZeros > drainedZeros) {
                result[ZERO_BUCKET] = (currentZeros - drainedZeros).toDouble()
                if (drain) drainedZeros = currentZeros
            }
            for (slot in 0 until BUCKETS_COUNT) {
                val count = counts[slot]?.sum() ?: continue
                val delta = count - drained[slot]
                if (delta > 0) {
                    result[(slot + MIN_INDEX).toString()] = delta.toDouble()
                    if (drain) drained[slot] = count
                }
            }
            return result
        }

        private companion object {

            /**
             * Index of the lowest bucket, -1036, containing 1e-9.
             */
            val MIN_INDEX = indexOf(1e-9)

            /**
             * Count of the slots, 3110, covering the buckets from 1e-9 to 1e18 with a relative accuracy of 1%.
             */
            val BUCKETS_COUNT = indexOf(1e18) - MIN_INDEX + 1
        }
    }
}
