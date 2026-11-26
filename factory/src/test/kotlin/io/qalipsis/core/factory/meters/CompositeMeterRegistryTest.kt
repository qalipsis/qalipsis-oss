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

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isSameAs
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Rate
import io.qalipsis.api.meters.Timer
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.meters.inmemory.InMemoryCumulativeMeterRegistry
import io.qalipsis.core.factory.redis.meters.RedisMeterRegistry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import java.util.Optional

@ExtendWith(MockKExtension::class)
internal class CompositeMeterRegistryTest {

    @MockK
    private lateinit var inMemoryRegistry: InMemoryCumulativeMeterRegistry

    @MockK
    private lateinit var redisRegistry: RedisMeterRegistry

    private val meterId = Meter.Id("my-meter", MeterType.COUNTER, mapOf("scenario" to "my-scenario"))

    @BeforeEach
    fun clearMocks() = clearAllMocks()

    @Test
    fun `given no Redis registry when a campaign runs on several factories then the meters are in memory`() = runTest {
        val registry = CompositeMeterRegistry(inMemoryRegistry, Optional.empty())
        val campaign = campaign(factoriesCount = 3)
        val counter = mockk<Counter>()
        val snapshot = mockk<MeterSnapshot>()
        coEvery { inMemoryRegistry.init(campaign) } just runs
        every { inMemoryRegistry.counter(meterId) } returns counter
        coEvery { inMemoryRegistry.snapshots(any()) } returns listOf(snapshot)

        registry.init(campaign)

        assertThat(registry.counter(meterId)).isSameAs(counter)
        assertThat(registry.snapshots(Instant.now())).containsOnly(snapshot)
        coVerify(exactly = 1) { inMemoryRegistry.init(campaign) }
    }

    @Test
    fun `given a single factory when the campaign runs then only the in-memory registry is initialized and closed`() = runTest {
        val registry = CompositeMeterRegistry(inMemoryRegistry, Optional.of(redisRegistry))
        val campaign = campaign(factoriesCount = 1)
        val gauge = mockk<Gauge>()
        coEvery { inMemoryRegistry.init(campaign) } just runs
        coEvery { inMemoryRegistry.close(campaign) } just runs
        every { inMemoryRegistry.gauge(meterId) } returns gauge

        registry.init(campaign)
        assertThat(registry.gauge(meterId)).isSameAs(gauge)
        registry.close(campaign)

        coVerifyOrder {
            inMemoryRegistry.init(campaign)
            inMemoryRegistry.gauge(meterId)
            inMemoryRegistry.close(campaign)
        }
        confirmVerified(inMemoryRegistry, redisRegistry)
    }

    @Test
    fun `given several factories when the campaign runs then only the Redis registry is initialized and closed`() = runTest {
        val registry = CompositeMeterRegistry(inMemoryRegistry, Optional.of(redisRegistry))
        val campaign = campaign(factoriesCount = 2)
        val rate = mockk<Rate>()
        val timer = mockk<Timer>()
        val redisSnapshot = mockk<MeterSnapshot>()
        coEvery { redisRegistry.init(campaign) } just runs
        coEvery { redisRegistry.close(campaign) } just runs
        every { redisRegistry.rate(meterId) } returns rate
        every { redisRegistry.timer(meterId, setOf(99.0)) } returns timer
        coEvery { redisRegistry.summarize(any()) } returns listOf(redisSnapshot)

        registry.init(campaign)
        assertThat(registry.rate(meterId)).isSameAs(rate)
        assertThat(registry.timer(meterId, setOf(99.0))).isSameAs(timer)
        assertThat(registry.summarize(Instant.now())).containsOnly(redisSnapshot)
        registry.close(campaign)

        coVerifyOrder {
            redisRegistry.init(campaign)
            redisRegistry.rate(meterId)
            redisRegistry.timer(meterId, setOf(99.0))
            redisRegistry.summarize(any())
            redisRegistry.close(campaign)
        }
        confirmVerified(inMemoryRegistry, redisRegistry)
    }

    private fun campaign(factoriesCount: Int) = Campaign(
        campaignKey = "my-campaign",
        broadcastChannel = "broadcast",
        feedbackChannel = "feedback",
        assignments = emptyList(),
        factoriesCount = factoriesCount
    )
}
