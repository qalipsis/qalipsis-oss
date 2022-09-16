/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.head.inmemory

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import java.time.Duration

@WithMockk
internal class InMemoryFactoryServiceTest {

    @RelaxedMockK
    private lateinit var scenarioSummaryRepository: ScenarioSummaryRepository

    @InjectMockKs
    private lateinit var inMemoryFactoryService: InMemoryFactoryService

    @Test
    internal fun `should save the scenarios from the request`() = runBlockingTest {
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
    internal fun `should returns the searched scenarios from the repository`() = runBlockingTest {
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
    internal fun `should returns the searched scenarios from the repository with sorting null`() = runBlockingTest {
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
    internal fun `should returns the searched scenarios from the repository with sorting asc`() = runBlockingTest {
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
    internal fun `should returns the searched scenarios from the repository with sorting desc`() = runBlockingTest {
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
    internal fun `should returns the searched scenarios from the repository with sorting`() = runBlockingTest {
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