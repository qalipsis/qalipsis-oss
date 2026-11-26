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

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import kotlin.math.ceil

internal class LogHistogramTest {

    @Test
    fun `given values over several orders of magnitude when computing the percentiles then the relative error is bounded`() {
        val values = (1..10_000).map { it * 1.7 }
        val buckets = values.groupingBy { LogHistogram.bucketOf(it) }.fold(0.0) { count, _ -> count + 1 }

        listOf(1.0, 25.0, 50.0, 90.0, 99.0, 99.9, 100.0).forEach { percentile ->
            val exact = values[ceil(percentile / 100 * values.size).toInt() - 1]
            assertThat(LogHistogram.percentile(buckets, percentile))
                .isBetween(exact * (1 - LogHistogram.RELATIVE_ACCURACY), exact * (1 + LogHistogram.RELATIVE_ACCURACY))
        }
    }

    @Test
    fun `given the documented values when bucketed then they match the documentation`() {
        assertThat(listOf(0.5, 1.0, 2.0, 100.0, 1000.0, 60000.0, 0.0, -3.0).map(LogHistogram::bucketOf))
            .isEqualTo(listOf("-34", "0", "35", "231", "346", "551", LogHistogram.ZERO_BUCKET, LogHistogram.ZERO_BUCKET))

        val buckets = mapOf("0" to 1.0, "35" to 2.0, "231" to 1.0)
        assertThat(LogHistogram.percentile(buckets, 50.0)).isCloseTo(1.9937, 0.0001)
        assertThat(LogHistogram.percentile(buckets, 99.0)).isCloseTo(100.4946, 0.0001)
    }

    @Test
    fun `given zeros and an empty histogram when computing the percentiles then they are 0`() {
        assertThat(LogHistogram.percentile(emptyMap(), 50.0)).isEqualTo(0.0)
        assertThat(LogHistogram.percentile(mapOf(LogHistogram.ZERO_BUCKET to 3.0), 99.0)).isEqualTo(0.0)
    }

    @Test
    fun `given histograms when merged then the counts of the buckets are summed`() {
        assertThat(LogHistogram.merge(mapOf("1" to 2.0, "2" to 1.0), mapOf("2" to 3.0, "5" to 1.0)))
            .isEqualTo(mapOf("1" to 2.0, "2" to 4.0, "5" to 1.0))
    }

    @Test
    fun `given a recorder when drained then only the new records are returned`() {
        val recorder = LogHistogram.Recorder()
        recorder.record(10.0)
        recorder.record(10.0)
        assertThat(recorder.drain()).isEqualTo(mapOf(LogHistogram.bucketOf(10.0) to 2.0))
        assertThat(recorder.undrained()).isEmpty()

        recorder.record(10.0)
        assertThat(recorder.undrained()).isEqualTo(mapOf(LogHistogram.bucketOf(10.0) to 1.0))
        assertThat(recorder.drain()).isEqualTo(mapOf(LogHistogram.bucketOf(10.0) to 1.0))
    }
}
