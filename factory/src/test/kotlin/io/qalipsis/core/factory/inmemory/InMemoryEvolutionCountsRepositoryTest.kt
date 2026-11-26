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

package io.qalipsis.core.factory.inmemory

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.qalipsis.core.factory.steps.evolution.EvolutionCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class InMemoryEvolutionCountsRepositoryTest {

    private lateinit var repository: InMemoryEvolutionCountsRepository

    @BeforeEach
    fun setup() {
        repository = InMemoryEvolutionCountsRepository()
    }

    @Test
    fun `given no transition when incrementing then no percentage is returned`() = runTest {
        repository.register(CAMPAIGN, SCENARIO, STEP)

        assertThat(repository.increment(CAMPAIGN, SCENARIO, STEP, 0, 0)).isNull()
    }

    @Test
    fun `given concurrent callers when incrementing then all the deltas are combined`() = runTest {
        withContext(Dispatchers.Default) {
            (1..8).map { async { repeat(1_000) { repository.increment(CAMPAIGN, SCENARIO, STEP, 3, 1) } } }.awaitAll()
        }

        assertThat(repository.increment(CAMPAIGN, SCENARIO, STEP, 0, 0)).isEqualTo(75.0)
    }

    @Test
    fun `given several instances when unregistering then only the last one is reported as last and deletes the counts`() =
        runTest {
            repository.register(CAMPAIGN, SCENARIO, STEP)
            repository.register(CAMPAIGN, SCENARIO, STEP)
            repository.increment(CAMPAIGN, SCENARIO, STEP, 990, 10)

            assertThat(repository.unregister(CAMPAIGN, SCENARIO, STEP, 0, 0)).isEqualTo(EvolutionCompletion(99.0, false))
            assertThat(repository.unregister(CAMPAIGN, SCENARIO, STEP, 1000, 0))
                .isEqualTo(EvolutionCompletion(99.5, true))
            // The counts were deleted: a new campaign of the same step starts from scratch.
            assertThat(repository.increment(CAMPAIGN, SCENARIO, STEP, 0, 0)).isNull()
        }

    @Test
    fun `given counts of different campaigns, scenarios and steps when incrementing then they are isolated`() = runTest {
        repository.increment(CAMPAIGN, SCENARIO, STEP, 1, 0)
        repository.increment("other-campaign", SCENARIO, STEP, 0, 1)
        repository.increment(CAMPAIGN, "other-scenario", STEP, 0, 1)
        repository.increment(CAMPAIGN, SCENARIO, "other-step", 0, 1)

        assertThat(repository.increment(CAMPAIGN, SCENARIO, STEP, 0, 0)).isEqualTo(100.0)
    }

    private companion object {

        const val CAMPAIGN = "my-campaign"

        const val SCENARIO = "my-scenario"

        const val STEP = "my-step"
    }
}
