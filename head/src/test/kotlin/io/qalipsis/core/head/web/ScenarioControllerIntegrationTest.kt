package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
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
@PropertySource(
    Property(name = "micronaut.server.log-handled-exceptions", value = "true"),
    Property(name = "identity.bind-tenant", value = "true")
)
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
            directedAcyclicGraphs = listOf(DirectedAcyclicGraphSummary("hi", tags = mapOf("one" to "one")))
        )
        val scenario2 = ScenarioSummary(
            name = "qalipsis-2",
            minionsCount = 2000,
            directedAcyclicGraphs = listOf(DirectedAcyclicGraphSummary("hello", tags = mapOf("two" to "two")))
        )
        coEvery { factoryService.getAllActiveScenarios("my-tenant", "name") } returns listOf(scenario, scenario2)

        val getAllScenariosRequest =
            HttpRequest.GET<List<ScenarioSummary>>("/scenarios?sort=name").header("X-Tenant", "my-tenant")

        // when
        val response: HttpResponse<List<ScenarioSummary>> = httpClient.toBlocking().exchange(
            getAllScenariosRequest,
            Argument.listOf(ScenarioSummary::class.java)
        )

        // then
        coVerifyOnce {
            factoryService.getAllActiveScenarios("my-tenant", "name")
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.all {
                hasSize(2)
                index(0).isDataClassEqualTo(scenario)
                index(1).isDataClassEqualTo(scenario2)
            }
        }
    }

    @Test
    fun `should return list of scenarios with sorting desc`() {
        // given
        val scenario = ScenarioSummary(
            name = "qalipsis-test",
            minionsCount = 1000,
            directedAcyclicGraphs = listOf(DirectedAcyclicGraphSummary("hi", tags = mapOf("one" to "one")))
        )
        val scenario2 = ScenarioSummary(
            name = "qalipsis-2",
            minionsCount = 2000,
            directedAcyclicGraphs = listOf(DirectedAcyclicGraphSummary("hello", tags = mapOf("two" to "two")))
        )
        coEvery { factoryService.getAllActiveScenarios("my-tenant", "name:desc") } returns listOf(scenario, scenario2)

        val getAllScenariosRequest =
            HttpRequest.GET<List<ScenarioSummary>>("/scenarios?sort=name:desc").header("X-Tenant", "my-tenant")

        // when
        val response: HttpResponse<List<ScenarioSummary>> = httpClient.toBlocking().exchange(
            getAllScenariosRequest,
            Argument.listOf(ScenarioSummary::class.java)
        )

        // then
        coVerifyOnce {
            factoryService.getAllActiveScenarios("my-tenant", "name:desc")
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.all {
                hasSize(2)
                index(0).isDataClassEqualTo(scenario)
                index(1).isDataClassEqualTo(scenario2)
            }
        }
    }

    @Test
    fun `should return list of scenarios without sorting`() {
        // given
        val scenario = ScenarioSummary(
            name = "qalipsis-test-2",
            minionsCount = 1000,
            directedAcyclicGraphs = listOf(DirectedAcyclicGraphSummary("hi", tags = mapOf("one" to "one")))
        )
        val scenario2 = ScenarioSummary(
            name = "qalipsis-3",
            minionsCount = 2000,
            directedAcyclicGraphs = listOf(DirectedAcyclicGraphSummary("hello", tags = mapOf("two" to "two")))
        )
        coEvery { factoryService.getAllActiveScenarios("my-tenant", null) } returns listOf(scenario, scenario2)

        val getAllScenariosRequest =
            HttpRequest.GET<List<ScenarioSummary>>("/scenarios").header("X-Tenant", "my-tenant")

        // when
        val response: HttpResponse<List<ScenarioSummary>> = httpClient.toBlocking().exchange(
            getAllScenariosRequest,
            Argument.listOf(ScenarioSummary::class.java)
        )

        // then
        coVerifyOnce {
            factoryService.getAllActiveScenarios("my-tenant", null)
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.all {
                hasSize(2)
                index(0).isDataClassEqualTo(scenario)
                index(1).isDataClassEqualTo(scenario2)
            }
        }
    }
}