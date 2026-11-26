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

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.core.configuration.ExecutionEnvironments
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration

internal class RedisMetersConfigurationIntegrationTest {

    @MicronautTest(
        environments = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.REDIS],
        packages = ["io.qalipsis.core.factory.redis.meters"],
        startApplication = false
    )
    @Nested
    inner class DefaultConfiguration {

        @Test
        @Timeout(10)
        fun `given the redis environment when no property is set then the configuration has the default values`(
            configuration: RedisMetersConfiguration,
        ) {
            assertThat(configuration).all {
                prop(RedisMetersConfiguration::enabled).isTrue()
                prop(RedisMetersConfiguration::linger).isEqualTo(Duration.ofSeconds(1))
                prop(RedisMetersConfiguration::finalTimeout).isEqualTo(Duration.ofSeconds(10))
                prop(RedisMetersConfiguration::keysTtl).isEqualTo(Duration.ofMinutes(1))
                prop(RedisMetersConfiguration::registryTtl).isEqualTo(Duration.ofHours(24))
                prop(RedisMetersConfiguration::shardedPubSub).isTrue()
                prop(RedisMetersConfiguration::shards).isEqualTo(16)
            }
        }
    }

    @MicronautTest(
        environments = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.REDIS],
        packages = ["io.qalipsis.core.factory.redis.meters"],
        startApplication = false
    )
    @PropertySource(
        Property(name = "meters.redis.enabled", value = "false"),
        Property(name = "meters.redis.linger", value = "500ms"),
        Property(name = "meters.redis.final-timeout", value = "20s"),
        Property(name = "meters.redis.keys-ttl", value = "2m"),
        Property(name = "meters.redis.registry-ttl", value = "12h"),
        Property(name = "meters.redis.sharded-pub-sub", value = "false"),
        Property(name = "meters.redis.shards", value = "8"),
    )
    @Nested
    inner class CustomConfiguration {

        @Test
        @Timeout(10)
        fun `given the properties when creating the configuration then they are bound`(configuration: RedisMetersConfiguration) {
            assertThat(configuration).all {
                prop(RedisMetersConfiguration::enabled).isFalse()
                prop(RedisMetersConfiguration::linger).isEqualTo(Duration.ofMillis(500))
                prop(RedisMetersConfiguration::finalTimeout).isEqualTo(Duration.ofSeconds(20))
                prop(RedisMetersConfiguration::keysTtl).isEqualTo(Duration.ofMinutes(2))
                prop(RedisMetersConfiguration::registryTtl).isEqualTo(Duration.ofHours(12))
                prop(RedisMetersConfiguration::shardedPubSub).isFalse()
                prop(RedisMetersConfiguration::shards).isEqualTo(8)
            }
        }
    }
}
