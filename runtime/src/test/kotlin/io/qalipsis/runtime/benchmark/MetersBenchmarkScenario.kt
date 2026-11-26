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

package io.qalipsis.runtime.benchmark

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.timeframe
import io.qalipsis.api.meters.steps.GaugeOperation
import io.qalipsis.api.meters.steps.RateIncrement
import io.qalipsis.api.meters.steps.counter
import io.qalipsis.api.meters.steps.gauge
import io.qalipsis.api.meters.steps.rate
import io.qalipsis.api.meters.steps.summary
import io.qalipsis.api.meters.steps.throughput
import io.qalipsis.api.meters.steps.timer
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.blackHole
import io.qalipsis.api.steps.flatten
import io.qalipsis.api.steps.returns
import java.time.Duration

/**
 * Scenario recording all the kinds of meters at a high rate, to benchmark the meters of a cluster of factories.
 *
 * Each minion emits [RECORDS_PER_MINION] values, each one passing through a step of each meter type.
 */
object MetersBenchmarkScenario {

    val MINIONS = System.getenv("BENCHMARK_MINIONS")?.toIntOrNull() ?: 2_000

    val RECORDS_PER_MINION = System.getenv("BENCHMARK_RECORDS_PER_MINION")?.toIntOrNull() ?: 500

    const val DISTINCT_VALUES = 100

    @Scenario("meters-benchmark")
    fun metersBenchmark() {
        scenario {
            minionsCount = MINIONS
            profile {
                timeframe(periodInMs = 500, timeFrameInMs = 5_000)
            }
        }.start()
            .returns { (1..RECORDS_PER_MINION).map { it % DISTINCT_VALUES + 1 } }
            .flatten()
            .counter("benchmark-counter") { _, _ -> 1.0 }
            .gauge("benchmark-gauge", GaugeOperation.ADD) { _, _ -> 1.0 }
            .rate("benchmark-rate") { _, value -> RateIncrement(if (value % 2 == 0) 1.0 else 0.0, 1.0) }
            .timer("benchmark-timer") { _, value -> Duration.ofMillis(value.toLong()) }
            .summary("benchmark-summary") { _, value -> value.toDouble() }
            .throughput("benchmark-throughput")
            .blackHole()
    }
}
