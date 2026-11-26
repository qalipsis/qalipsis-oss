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

package io.qalipsis.core.factory.redis

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.steps.evolution.EvolutionCompletion
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@ExperimentalLettuceCoroutinesApi
@MicronautTest(environments = [ExecutionEnvironments.REDIS, ExecutionEnvironments.FACTORY], startApplication = false)
internal class RedisEvolutionCountsRepositoryIntegrationTest : AbstractRedisIntegrationTest() {

    @Inject
    private lateinit var repository: RedisEvolutionCountsRepository

    @AfterEach
    fun tearDown() = runBlocking<Unit> {
        redisCoroutinesCommands.flushall()
    }

    @Test
    @Timeout(10)
    fun `given registered instances when incrementing then the combined percentage is returned and the counts kept`() =
        runBlocking<Unit> {
            repository.register(CAMPAIGN, SCENARIO, STEP)
            repository.register(CAMPAIGN, SCENARIO, STEP)

            assertThat(repository.increment(CAMPAIGN, SCENARIO, STEP, 990, 0)).isEqualTo(100.0)
            assertThat(repository.increment(CAMPAIGN, SCENARIO, STEP, 0, 10)).isEqualTo(99.0)

            assertThat(redisCoroutinesCommands.hgetall(KEY).toList().associate { it.key to it.value })
                .isEqualTo(mapOf("valid" to "990", "invalid" to "10", "instances" to "2"))
            assertThat(redisCoroutinesCommands.ttl(KEY)!!).isBetween(1, 24 * 3600)
        }

    @Test
    @Timeout(10)
    fun `given no transition when incrementing then no percentage is returned`() = runBlocking<Unit> {
        repository.register(CAMPAIGN, SCENARIO, STEP)

        assertThat(repository.increment(CAMPAIGN, SCENARIO, STEP, 0, 0)).isNull()
    }

    @Test
    @Timeout(20)
    fun `given concurrent callers when incrementing then all the deltas are combined`() = runBlocking<Unit> {
        repository.register(CAMPAIGN, SCENARIO, STEP)

        (1..8).map {
            async(Dispatchers.IO) {
                repeat(100) { repository.increment(CAMPAIGN, SCENARIO, STEP, 3, 1) }
            }
        }.awaitAll()

        assertThat(repository.increment(CAMPAIGN, SCENARIO, STEP, 0, 0)).isEqualTo(75.0)
        assertThat(redisCoroutinesCommands.hmget(KEY, "valid", "invalid").toList().map { it.value })
            .isEqualTo(listOf("2400", "800"))
    }

    @Test
    @Timeout(10)
    fun `given several instances when unregistering then only the last one is reported as last and deletes the counts`() =
        runBlocking<Unit> {
            repository.register(CAMPAIGN, SCENARIO, STEP)
            repository.register(CAMPAIGN, SCENARIO, STEP)
            repository.increment(CAMPAIGN, SCENARIO, STEP, 990, 10)

            assertThat(repository.unregister(CAMPAIGN, SCENARIO, STEP, 5, 0))
                .isEqualTo(EvolutionCompletion(995 * 100.0 / 1005, last = false))
            assertThat(redisCoroutinesCommands.exists(KEY)).isEqualTo(1L)

            assertThat(repository.unregister(CAMPAIGN, SCENARIO, STEP, 0, 0))
                .isEqualTo(EvolutionCompletion(995 * 100.0 / 1005, last = true))
            assertThat(redisCoroutinesCommands.exists(KEY)).isEqualTo(0L)
        }

    @Test
    @Timeout(10)
    fun `given an instance without transition when unregistering then it is last without percentage`() =
        runBlocking<Unit> {
            repository.register(CAMPAIGN, SCENARIO, STEP)

            assertThat(repository.unregister(CAMPAIGN, SCENARIO, STEP, 0, 0)).isEqualTo(EvolutionCompletion(null, true))
        }

    @Test
    @Timeout(10)
    fun `given counts of different campaigns, scenarios and steps when incrementing then they are isolated`() =
        runBlocking<Unit> {
            assertThat(repository.increment(CAMPAIGN, SCENARIO, STEP, 1, 0)).isEqualTo(100.0)
            assertThat(repository.increment("other-campaign", SCENARIO, STEP, 1, 1)).isEqualTo(50.0)
            assertThat(repository.increment(CAMPAIGN, "other-scenario", STEP, 1, 3)).isEqualTo(25.0)
            assertThat(repository.increment(CAMPAIGN, SCENARIO, "other-step", 0, 1)).isEqualTo(0.0)

            assertThat(repository.increment(CAMPAIGN, SCENARIO, STEP, 0, 0)).isEqualTo(100.0)
            assertThat(redisCoroutinesCommands.keys("qevolution:*").toList()).containsOnly(
                KEY,
                "qevolution:{_qalipsis_ten_:other-campaign:$SCENARIO:$STEP}",
                "qevolution:{_qalipsis_ten_:$CAMPAIGN:other-scenario:$STEP}",
                "qevolution:{_qalipsis_ten_:$CAMPAIGN:$SCENARIO:other-step}"
            )
        }

    @Test
    @Timeout(10)
    fun `given the scripts were flushed from Redis when calling then the script is loaded again`() =
        runBlocking<Unit> {
            repository.register(CAMPAIGN, SCENARIO, STEP)
            redisCoroutinesCommands.scriptFlush()

            assertThat(repository.increment(CAMPAIGN, SCENARIO, STEP, 1, 0)).isEqualTo(100.0)
            assertThat(redisCoroutinesCommands.hexists(KEY, "instances")!!).isTrue()
            assertThat(redisCoroutinesCommands.hexists(KEY, "unknown")!!).isFalse()
        }

    private companion object {

        const val CAMPAIGN = "my-campaign"

        const val SCENARIO = "my-scenario"

        const val STEP = "my-step"

        const val KEY = "qevolution:{_qalipsis_ten_:$CAMPAIGN:$SCENARIO:$STEP}"
    }
}
