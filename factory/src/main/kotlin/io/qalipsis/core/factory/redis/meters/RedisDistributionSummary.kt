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

import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.Meter
import io.qalipsis.core.reporter.MeterReporter

/**
 * [DistributionSummary] combined in Redis.
 *
 * @param id identifier of the distribution summary
 * @param levels levels of the distribution summary in Redis: the distribution summary itself, then its levels
 *   without zone and without scenario, when it has these tags (see [RedisMeter.levels])
 * @param meterReporter reporter displaying the meter in the campaign report, when configured with [report]
 * @property percentiles percentiles (0 to 100) to compute from the combined histogram and publish, for example 50.0
 *   and 99.0
 */
internal class RedisDistributionSummary(
    id: Meter.Id,
    levels: List<RedisMeterLevel>,
    meterReporter: MeterReporter,
    override val percentiles: Collection<Double>,
) : RedisMeter<DistributionSummary>(id, levels, meterReporter), DistributionSummary {

    private val distribution = RedisDistribution(level)

    override fun record(amount: Double) {
        if (amount >= 0) {
            distribution.record(amount)
        }
    }

    override fun count() = distribution.count()

    override fun totalAmount() = distribution.total()

    override fun mean() = distribution.mean()

    override fun max() = distribution.max()

    override fun percentile(percentile: Double) = distribution.percentile(percentile)

    override fun drainInto(levels: List<RedisMeterLevel>, complete: Boolean) = distribution.drainInto(levels, complete)
}
