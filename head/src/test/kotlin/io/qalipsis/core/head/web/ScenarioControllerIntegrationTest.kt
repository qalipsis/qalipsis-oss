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

package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
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
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import jakarta.inject.Inject
import org.junit.jupiter.api.Test

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.TRANSIENT, ExecutionEnvironments.SINGLE_HEAD])
@PropertySource(Property(name = "micronaut.server.log-handled-exceptions", value = "true"))
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
        coEvery { factoryService.getAllActiveScenarios(Defaults.TENANT, "name") } returns listOf(scenario, scenario2)

        val getAllScenariosRequest =
            HttpRequest.GET<List<ScenarioSummary>>("/scenarios?sort=name")

        // when
        val response: HttpResponse<List<ScenarioSummary>> = httpClient.toBlocking().exchange(
            getAllScenariosRequest,
            Argument.listOf(ScenarioSummary::class.java)
        )

        // then
        coVerifyOnce {
            factoryService.getAllActiveScenarios(Defaults.TENANT, "name")
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isNotNull().all {
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
        coEvery { factoryService.getAllActiveScenarios(Defaults.TENANT, "name:desc") } returns listOf(
            scenario,
            scenario2
        )

        val getAllScenariosRequest =
            HttpRequest.GET<List<ScenarioSummary>>("/scenarios?sort=name:desc")

        // when
        val response: HttpResponse<List<ScenarioSummary>> = httpClient.toBlocking().exchange(
            getAllScenariosRequest,
            Argument.listOf(ScenarioSummary::class.java)
        )

        // then
        coVerifyOnce {
            factoryService.getAllActiveScenarios(Defaults.TENANT, "name:desc")
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isNotNull().all {
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
        coEvery { factoryService.getAllActiveScenarios(Defaults.TENANT, null) } returns listOf(scenario, scenario2)

        val getAllScenariosRequest =
            HttpRequest.GET<List<ScenarioSummary>>("/scenarios")

        // when
        val response: HttpResponse<List<ScenarioSummary>> = httpClient.toBlocking().exchange(
            getAllScenariosRequest,
            Argument.listOf(ScenarioSummary::class.java)
        )

        // then
        coVerifyOnce {
            factoryService.getAllActiveScenarios(Defaults.TENANT, null)
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isNotNull().all {
                hasSize(2)
                index(0).isDataClassEqualTo(scenario)
                index(1).isDataClassEqualTo(scenario2)
            }
        }
    }
}