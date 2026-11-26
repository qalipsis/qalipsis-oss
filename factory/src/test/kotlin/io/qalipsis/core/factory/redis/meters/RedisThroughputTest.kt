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
import assertk.assertions.isEqualTo
import io.mockk.mockk
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterType
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.HITS_FIELD
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit

internal class RedisThroughputTest {

    private val level = RedisMeterLevel(Meter.Id("my-throughput", MeterType.THROUGHPUT, emptyMap()), intervalMillis = 1000)

    private val throughput = RedisThroughput(level.meterId, listOf(level), mockk(relaxed = true), ChronoUnit.SECONDS, listOf(50.0))

    @Test
    fun `given cached statistics when the combined values are refreshed then the statistics are recomputed`() {
        level.cumulative = mapOf(HITS_FIELD to mapOf("1000" to 5.0))
        assertThat(throughput.current()).isEqualTo(5.0)
        assertThat(throughput.max()).isEqualTo(5.0)

        level.cumulative = mapOf(HITS_FIELD to mapOf("1000" to 5.0, "2000" to 8.0))

        assertThat(throughput.current()).isEqualTo(8.0)
        assertThat(throughput.mean()).isEqualTo(6.5)
        assertThat(throughput.max()).isEqualTo(8.0)
    }
}
