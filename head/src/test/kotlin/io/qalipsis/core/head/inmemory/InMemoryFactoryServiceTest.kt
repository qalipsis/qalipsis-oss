package io.qalipsis.core.head.inmemory

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test

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

        // when
        inMemoryFactoryService.register("", request)

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
        val result = inMemoryFactoryService.getActiveScenarios(listOf("scen-1", "scen-2"))

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


}