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

import io.lettuce.core.AbstractRedisClient
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.coroutines
import io.qalipsis.core.redis.RedisRuntimeConfiguration
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@OptIn(ExperimentalLettuceCoroutinesApi::class)
@Testcontainers
internal class StandaloneRedisMeterRegistryIntegrationTest : AbstractRedisMeterRegistryIntegrationTest() {

    override fun createClient(): AbstractRedisClient =
        RedisClient.create("redis://localhost:${CONTAINER.getMappedPort(RedisRuntimeConfiguration.DEFAULT_PORT)}")

    override fun createCommands(client: AbstractRedisClient) = (client as RedisClient).connect().coroutines().let {
        RedisMeterTestCommands(it, it, it, it, it)
    }

    companion object {

        @JvmStatic
        @Container
        val CONTAINER = RedisRuntimeConfiguration.createContainer()
    }
}
