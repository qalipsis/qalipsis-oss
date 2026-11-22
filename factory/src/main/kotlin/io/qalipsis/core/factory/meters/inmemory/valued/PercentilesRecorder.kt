/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.meters.inmemory.valued

import org.HdrHistogram.Histogram
import org.HdrHistogram.Recorder
import kotlin.math.ceil

/**
 * Records values to compute their percentiles, without blocking nor sorting in the recording path.
 *
 * The values are recorded into a [Recorder] of HdrHistogram: recording is wait-free, a few array increments. The
 * values recorded since the previous read are collected into [values] only when the percentiles are read, so the
 * cost is paid by the reader (snapshots, console) and not by the recording threads.
 *
 * The precision is [SIGNIFICANT_DIGITS] significant digits (values within 1 %); the histograms are resized
 * automatically, so any non-negative value can be recorded.
 */
internal class PercentilesRecorder {

    private val recorder = Recorder(SIGNIFICANT_DIGITS)

    /**
     * Values collected from [recorder] and not reset yet, only accessed under the lock of this instance.
     */
    private val values = Histogram(SIGNIFICANT_DIGITS)

    /**
     * Histogram given back to [recorder] at the next collection, to avoid allocating one each time.
     */
    private var recycled: Histogram? = null

    /**
     * Records a non-negative [value].
     */
    fun record(value: Long) {
        recorder.recordValue(value)
    }

    /**
     * Returns the value at [percentile] (from 0 to 100) of all the values recorded since the last [drain], or NaN when
     * there is none, as the former TDigest-based implementation.
     */
    @Synchronized
    fun percentile(percentile: Double): Double {
        collect()
        return valueAt(percentile)
    }

    /**
     * Returns the values at [percentiles] of the values recorded since the last call, and forgets them.
     * The values recorded while this function runs are kept for the next call.
     */
    @Synchronized
    fun drain(percentiles: Collection<Double>): List<Double> {
        collect()
        val result = percentiles.map(::valueAt)
        values.reset()
        return result
    }

    private fun collect() {
        val interval = recorder.getIntervalHistogram(recycled)
        values.add(interval)
        recycled = interval
    }

    /**
     * Returns the value at [percentile] with the nearest-rank definition: the smallest recorded value such that at
     * least [percentile] % of the values are lower or equal, i.e. the value of rank `ceil(percentile × count)`.
     * [Histogram.getValueAtPercentile] rounds the rank to the nearest integer instead, which returns a lower value
     * for small counts (e.g. the first of 5 values as the 25th percentile).
     */
    private fun valueAt(percentile: Double): Double {
        val total = values.totalCount
        if (total == 0L) {
            return Double.NaN
        }
        val rank = ceil(percentile / 100 * total).toLong().coerceIn(1, total)
        var count = 0L
        for (value in values.recordedValues()) {
            count += value.countAtValueIteratedTo
            if (count >= rank) {
                return values.highestEquivalentValue(value.valueIteratedTo).toDouble()
            }
        }
        return values.maxValue.toDouble()
    }

    private companion object {

        const val SIGNIFICANT_DIGITS = 2
    }
}
