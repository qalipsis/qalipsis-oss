package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.campaigns.DirectedAcyclicGraphSummary
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import jakarta.inject.Inject
import org.junit.jupiter.api.Test

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.VOLATILE, ExecutionEnvironments.SINGLE_HEAD])
internal class ScenarioControllerIntegrationTest {

    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

    @RelaxedMockK
    private lateinit var factoryService: FactoryService

    @MockBean(FactoryService::class)
    fun factoryService() = factoryService

    @Test
    fun `should return list of scenarios`() {
        // given
        val scenario = ScenarioSummary(
            name = "qalipsis-test",
            minionsCount = 1000,
            directedAcyclicGraphs = listOf(DirectedAcyclicGraphSummary("hi", selectors = mapOf("one" to "one")))
        )
        val scenario2 = ScenarioSummary(
            name = "qalipsis-2",
            minionsCount = 2000,
            directedAcyclicGraphs = listOf(DirectedAcyclicGraphSummary("hello", selectors = mapOf("two" to "two")))
        )
        coEvery { factoryService.getAllActiveScenarios("qalipsis", "name") } returns listOf(scenario, scenario2)

        val getAllScenariosRequest =
            HttpRequest.GET<List<ScenarioSummary>>("/scenarios?sort=name").header("X-Tenant", "qalipsis")

        // when
        val response: HttpResponse<List<ScenarioSummary>> = httpClient.toBlocking().exchange(
            getAllScenariosRequest,
            Argument.listOf(ScenarioSummary::class.java)
        )

        // then
        coVerifyOnce {
            factoryService.getAllActiveScenarios("qalipsis", "name")
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.hasSize(2)
            transform("body") { it.body().get(0) }.isDataClassEqualTo(scenario)
            transform("body") { it.body().get(1) }.isDataClassEqualTo(scenario2)
        }
    }
}