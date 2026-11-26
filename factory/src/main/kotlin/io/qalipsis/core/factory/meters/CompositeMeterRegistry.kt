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

package io.qalipsis.core.factory.meters

import io.micronaut.context.annotation.Primary
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.Rate
import io.qalipsis.api.meters.Throughput
import io.qalipsis.api.meters.Timer
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.campaign.CampaignLifeCycleAware
import io.qalipsis.core.factory.meters.inmemory.InMemoryCumulativeMeterRegistry
import io.qalipsis.core.factory.redis.meters.RedisMeterRegistry
import jakarta.inject.Singleton
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

/**
 * [MeterRegistry] selecting at the start of each campaign where its meters are kept: in Redis when the campaign runs
 * on several factories and the Redis registry is available, in memory otherwise.
 *
 * @author Eric Jessé
 */
@Primary
@Singleton
internal class CompositeMeterRegistry(
    private val inMemoryRegistry: InMemoryCumulativeMeterRegistry,
    redisRegistry: Optional<RedisMeterRegistry>,
) : MeterRegistry, CampaignLifeCycleAware {

    private val redisRegistry: RedisMeterRegistry? = redisRegistry.orElse(null)

    /**
     * Registry of the meters of the current campaign.
     */
    @Volatile
    private var sharedRegistry: MeterRegistry = inMemoryRegistry

    override suspend fun init(campaign: Campaign) {
        val selected = redisRegistry?.takeIf { campaign.factoriesCount > 1 } ?: inMemoryRegistry
        selected.init(campaign)
        sharedRegistry = selected
    }

    override suspend fun close(campaign: Campaign) {
        try {
            sharedRegistry.close(campaign)
        } finally {
            sharedRegistry = inMemoryRegistry
        }
    }

    override fun counter(meterId: Meter.Id): Counter = sharedRegistry.counter(meterId)

    override fun gauge(meterId: Meter.Id): Gauge = sharedRegistry.gauge(meterId)

    override fun rate(meterId: Meter.Id): Rate = sharedRegistry.rate(meterId)

    override fun timer(meterId: Meter.Id, percentiles: Set<Double>): Timer = sharedRegistry.timer(meterId, percentiles)

    override fun summary(meterId: Meter.Id, percentiles: Set<Double>): DistributionSummary =
        sharedRegistry.summary(meterId, percentiles)

    override fun throughput(meterId: Meter.Id, unit: ChronoUnit, percentiles: Set<Double>): Throughput =
        sharedRegistry.throughput(meterId, unit, percentiles)

    override suspend fun snapshots(instant: Instant): Collection<MeterSnapshot> = sharedRegistry.snapshots(instant)

    override suspend fun summarize(instant: Instant): Collection<MeterSnapshot> = sharedRegistry.summarize(instant)
}
