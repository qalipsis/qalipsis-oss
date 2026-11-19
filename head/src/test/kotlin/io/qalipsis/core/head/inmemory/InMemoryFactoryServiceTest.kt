/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.inmemory

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

@WithMockk
internal class InMemoryFactoryServiceTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var scenarioSummaryRepository: ScenarioSummaryRepository

    @InjectMockKs
    private lateinit var inMemoryFactoryService: InMemoryFactoryService

    @Test
    internal fun `should save the scenarios from the request`() = testDispatcherProvider.runTest {
        // given
        val scenario1 = relaxedMockk<ScenarioSummary>()
        val scenario2 = relaxedMockk<ScenarioSummary>()
        val request = HandshakeRequest("", emptyMap(), "", listOf(scenario1, scenario2))
        val response = HandshakeResponse(
            handshakeNodeId = "testNodeId",
            nodeId = "",
            unicastChannel = "directives-unicast",
            heartbeatChannel = "heartbeat",
            heartbeatPeriod = Duration.ofMinutes(1)
        )

        // when
        inMemoryFactoryService.register("", request, response)

        // then
        coVerify {
            scenarioSummaryRepository.saveAll(listOf(scenario1, scenario2))
        }
        confirmVerified(scenarioSummaryRepository)
    }

    @Test
    internal fun `should return the tenant of the factory`() = testDispatcherProvider.runTest {
        // given
        val scenario1 = relaxedMockk<ScenarioSummary>()
        val scenario2 = relaxedMockk<ScenarioSummary>()
        val request = HandshakeRequest(
            nodeId = "",
            tags = emptyMap(),
            replyTo = "",
            scenarios = listOf(scenario1, scenario2),
            tenant = "my-tenant"
        )
        val response = HandshakeResponse(
            handshakeNodeId = "testNodeId",
            nodeId = "",
            unicastChannel = "directives-unicast",
            heartbeatChannel = "heartbeat",
            heartbeatPeriod = Duration.ofMinutes(1)
        )
        inMemoryFactoryService.register("the-real-node-id", request, response)

        // when
        val tenant = inMemoryFactoryService.getFactoryTenant("the-real-node-id")

        // then
        assertThat(tenant).isEqualTo("my-tenant")
    }

    @Test
    internal fun `given a node ID when checking whether it is owned by another tenant then only another tenant matches`() =
        testDispatcherProvider.runTest {
            // given
            val request = HandshakeRequest(
                nodeId = "",
                tags = emptyMap(),
                replyTo = "",
                scenarios = emptyList(),
                tenant = "my-tenant"
            )
            inMemoryFactoryService.register("the-real-node-id", request, relaxedMockk())

            // when + then
            assertThat(inMemoryFactoryService.isNodeIdOwnedByAnotherTenant("my-tenant", "the-real-node-id")).isFalse()
            assertThat(inMemoryFactoryService.isNodeIdOwnedByAnotherTenant("other-tenant", "the-real-node-id")).isTrue()
            assertThat(inMemoryFactoryService.isNodeIdOwnedByAnotherTenant("other-tenant", "unknown-node-id")).isFalse()
        }

    @Test
    internal fun `should returns the searched scenarios from the repository`() = testDispatcherProvider.runTest {
        // given
        val scenario1 = relaxedMockk<ScenarioSummary>()
        val scenario2 = relaxedMockk<ScenarioSummary>()
        every { scenarioSummaryRepository.getAll(any()) } returns listOf(scenario1, scenario2)

        // when
        val result = inMemoryFactoryService.getActiveScenarios("", listOf("scen-1", "scen-2"))

        // then
        assertThat(result).all {
            hasSize(2)
            containsOnly(scenario1, scenario2)
        }
        coVerify {
            scenarioSummaryRepository.getAll(listOf("scen-1", "scen-2"))
        }
        confirmVerified(scenarioSummaryRepository)
    }

    @Test
    internal fun `should returns the searched scenarios from the repository with sorting null`() =
        testDispatcherProvider.runTest {
            // given
            val scenario1 = relaxedMockk<ScenarioSummary>()
            val scenario2 = relaxedMockk<ScenarioSummary>()
            val scenarios = listOf(scenario1, scenario2)
            every { scenarioSummaryRepository.getAll() } returns scenarios

            // when
            val result = inMemoryFactoryService.getAllActiveScenarios("my-tenant", null)

            // then
            assertThat(result).all {
                containsOnly(scenario1, scenario2)
                hasSize(2)
                isEqualTo(scenarios)
            }
            coVerify {
                scenarioSummaryRepository.getAll()
            }
            confirmVerified(scenarioSummaryRepository)
        }

    @Test
    internal fun `should returns the searched scenarios from the repository with sorting asc`() =
        testDispatcherProvider.runTest {
            // given
            val scenario1 = relaxedMockk<ScenarioSummary>()
            val scenario2 = relaxedMockk<ScenarioSummary>()
            val scenarios = listOf(scenario1, scenario2)
            every { scenarioSummaryRepository.getAll() } returns scenarios

            // when
            val result = inMemoryFactoryService.getAllActiveScenarios("my-tenant", "name:asc")

            // then
            assertThat(result).all {
                containsOnly(scenario1, scenario2)
                hasSize(2)
                isEqualTo(scenarios)
            }
            coVerify {
                scenarioSummaryRepository.getAll()
            }
            confirmVerified(scenarioSummaryRepository)
        }

    @Test
    internal fun `should returns the searched scenarios from the repository with sorting desc`() =
        testDispatcherProvider.runTest {
            // given
            val scenario1 = relaxedMockk<ScenarioSummary>()
            val scenario2 = relaxedMockk<ScenarioSummary>()
            val scenarios = listOf(scenario1, scenario2)
            every { scenarioSummaryRepository.getAll() } returns scenarios

            // when
            val result = inMemoryFactoryService.getAllActiveScenarios("my-tenant", "name:desc")

            // then
            assertThat(result).all {
                containsOnly(scenario1, scenario2)
                hasSize(2)
                isEqualTo(scenarios.reversed())
            }
            coVerify {
                scenarioSummaryRepository.getAll()
            }
            confirmVerified(scenarioSummaryRepository)
        }

    @Test
    internal fun `should returns the searched scenarios from the repository with sorting`() =
        testDispatcherProvider.runTest {
            // given
            val scenario1 = relaxedMockk<ScenarioSummary>()
            val scenario2 = relaxedMockk<ScenarioSummary>()
            val scenarios = listOf(scenario1, scenario2)
            every { scenarioSummaryRepository.getAll() } returns scenarios

            // when
            val result = inMemoryFactoryService.getAllActiveScenarios("my-tenant", "name")

            // then
            assertThat(result).all {
                containsOnly(scenario1, scenario2)
                hasSize(2)
                isEqualTo(scenarios)
            }
            coVerify {
                scenarioSummaryRepository.getAll()
            }
            confirmVerified(scenarioSummaryRepository)
        }
}