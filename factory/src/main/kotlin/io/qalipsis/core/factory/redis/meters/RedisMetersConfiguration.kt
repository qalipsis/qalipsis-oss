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

import io.micronaut.context.annotation.ConfigurationProperties
import io.qalipsis.api.constraints.PositiveDuration
import java.time.Duration
import javax.validation.constraints.Positive

/**
 * Configuration of the combination of the meters in Redis, when a campaign runs on several factories.
 *
 * All the factories of a campaign must use the same values for [shards] and [shardedPubSub].
 *
 * @author Eric Jessé
 */
@ConfigurationProperties("meters.redis")
class RedisMetersConfiguration {

    /**
     * Set to false to keep the meters of each factory in memory, even when a campaign runs on several factories.
     */
    var enabled: Boolean = true

    /**
     * Period of the bulk flushes of the meters to Redis.
     */
    @field:PositiveDuration
    var linger: Duration = Duration.ofSeconds(1)

    /**
     * Maximal duration to wait for the final values of the owned meters at the end of the campaign.
     */
    @field:PositiveDuration
    var finalTimeout: Duration = Duration.ofSeconds(10)

    /**
     * Safety TTL of the keys of the meter shards, refreshed at each acknowledgement of a period.
     */
    @field:PositiveDuration
    var keysTtl: Duration = Duration.ofMinutes(1)

    /**
     * Safety TTL of the keys of the declared meters, refreshed at each declaration.
     */
    @field:PositiveDuration
    var registryTtl: Duration = Duration.ofHours(24)

    /**
     * Publishes the values of the meters with the sharded Pub/Sub (Redis 7.0 or later); set to false to use the plain
     * Pub/Sub on older servers, which broadcasts each message to all the nodes of a Redis cluster.
     */
    var shardedPubSub: Boolean = true

    /**
     * Count of shards distributing the meters over the nodes of a Redis cluster, flushed concurrently.
     */
    @field:Positive
    var shards: Int = 16
}
