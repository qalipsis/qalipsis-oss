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

package io.qalipsis.core.factory.steps.evolution

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.runs
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.inmemory.InMemoryEvolutionCountsRepository
import io.qalipsis.core.factory.redis.RedisEvolutionCountsRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Optional

@ExtendWith(MockKExtension::class)
internal class CompositeEvolutionCountsRepositoryTest {

    @MockK
    private lateinit var inMemoryRepository: InMemoryEvolutionCountsRepository

    @MockK
    private lateinit var redisRepository: RedisEvolutionCountsRepository

    @BeforeEach
    fun clearMocks() = clearAllMocks()

    @Test
    fun `given no Redis repository when a campaign runs on several factories then the counts are in memory`() = runTest {
        val repository = CompositeEvolutionCountsRepository(inMemoryRepository, Optional.empty())

        repository.init(campaign(factoriesCount = 3))
        runSteps(repository, inMemoryRepository)
    }

    @Test
    fun `given a single factory when the campaign runs then the counts are in memory`() = runTest {
        val repository = CompositeEvolutionCountsRepository(inMemoryRepository, Optional.of(redisRepository))

        repository.init(campaign(factoriesCount = 1))
        runSteps(repository, inMemoryRepository)
    }

    @Test
    fun `given several factories when the campaign runs then the counts are in Redis, also after the closing`() = runTest {
        val repository = CompositeEvolutionCountsRepository(inMemoryRepository, Optional.of(redisRepository))
        val campaign = campaign(factoriesCount = 2)

        repository.init(campaign)
        repository.close(campaign)
        runSteps(repository, redisRepository)
    }

    @Test
    fun `given a campaign on several factories when the next one runs on a single factory then the counts are in memory`() =
        runTest {
            val repository = CompositeEvolutionCountsRepository(inMemoryRepository, Optional.of(redisRepository))

            repository.init(campaign(factoriesCount = 2))
            repository.init(campaign(factoriesCount = 1))
            runSteps(repository, inMemoryRepository)
        }

    /**
     * Executes all the operations on [repository] and verifies that they are all delegated to [expected].
     */
    private suspend fun runSteps(repository: CompositeEvolutionCountsRepository, expected: EvolutionCountsRepository) {
        val completion = EvolutionCompletion(percentile = 75.0, last = true)
        coEvery { expected.register(CAMPAIGN, SCENARIO, STEP) } just runs
        coEvery { expected.increment(CAMPAIGN, SCENARIO, STEP, 3, 1) } returns 75.0
        coEvery { expected.unregister(CAMPAIGN, SCENARIO, STEP, 0, 0) } returns completion

        repository.register(CAMPAIGN, SCENARIO, STEP)
        assertThat(repository.increment(CAMPAIGN, SCENARIO, STEP, 3, 1)).isEqualTo(75.0)
        assertThat(repository.unregister(CAMPAIGN, SCENARIO, STEP, 0, 0)).isEqualTo(completion)

        coVerifyOrder {
            expected.register(CAMPAIGN, SCENARIO, STEP)
            expected.increment(CAMPAIGN, SCENARIO, STEP, 3, 1)
            expected.unregister(CAMPAIGN, SCENARIO, STEP, 0, 0)
        }
        confirmVerified(inMemoryRepository, redisRepository)
    }

    private fun campaign(factoriesCount: Int) = Campaign(
        campaignKey = CAMPAIGN,
        broadcastChannel = "broadcast",
        feedbackChannel = "feedback",
        assignments = emptyList(),
        factoriesCount = factoriesCount
    )

    private companion object {

        const val CAMPAIGN = "my-campaign"

        const val SCENARIO = "my-scenario"

        const val STEP = "my-step"
    }
}
