package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignManager
import io.qalipsis.core.head.factory.ClusterFactoryService
import io.qalipsis.core.head.jdbc.entity.ScenarioEntity
import io.qalipsis.core.head.web.model.CampaignConfigurationConverter
import io.qalipsis.core.head.web.model.CampaignRequest
import io.qalipsis.core.head.web.model.ScenarioRequest
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.SINGLE_HEAD])
internal class CampaignControllerIntegrationTest {

    @Inject
    @field:Client("/campaigns")
    lateinit var httpClient: HttpClient

    @RelaxedMockK
    private lateinit var campaignManager: CampaignManager

    @RelaxedMockK
    private lateinit var clusterFactoryService: ClusterFactoryService

    @RelaxedMockK
    private lateinit var campaignConfigurationConverter: CampaignConfigurationConverter

    @MockBean(ClusterFactoryService::class)
    fun clusterFactoryService() = clusterFactoryService

    @MockBean(CampaignManager::class)
    fun campaignManager() = campaignManager

    @MockBean(CampaignConfigurationConverter::class)
    fun campaignConfigurationConverter() = campaignConfigurationConverter

    @Test
    fun `should return status accepted when start campaign`() {
        // given
        val campaignRequest = CampaignRequest(
            name = "just-test",
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )
        val executeRequest = HttpRequest.POST("/", campaignRequest).header("X-Tenant", "qalipsis")

        // when
        val response = httpClient.toBlocking().exchange(
            executeRequest,
            CampaignRequest::class.java
        )

        // then
        coVerifyOnce {
            campaignManager.start("", any())
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.ACCEPTED)
        }
    }

    @Test
    fun `should return 400 for invalid campaign configuration`() {
        // given
        val campaignRequest = CampaignRequest(
            name = "ju",
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(5))
        )
        val executeRequest = HttpRequest.POST("/", campaignRequest).header("X-Tenant", "qalipsis")

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                executeRequest,
                CampaignRequest::class.java
            )
        }

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
            transform("body") {
                it.response.getBody(String::class.java).get()
            }.contains(""""message":"campaign.name: size must be between 3 and 300"""")
        }
    }


    @Test
    fun `should return status accepted for valid campaign`() {
        // given
        val campaignRequest = CampaignRequest(
            name = "just",
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(5))
        )
        val validateRequest = HttpRequest.POST("/validate", campaignRequest)
            .header("X-Tenant", "qalipsis")
            .header("Accept-Language", "en")
        coEvery { clusterFactoryService.getActiveScenarios("qalipsis", any()) } returns listOf(
            ScenarioEntity(555, "scenario-1", 500)
        ).map(ScenarioEntity::toModel)

        // when
        val response = httpClient.toBlocking().exchange(validateRequest, Unit::class.java)
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.ACCEPTED)
        }
    }

    @Test
    fun `should return status failure for not existing campaign`() {
        // given
        val campaignRequest = CampaignRequest(
            name = "just",
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(5))
        )
        coEvery { clusterFactoryService.getActiveScenarios("qalipsis", any()) } returns emptyList()
        val validateRequest = HttpRequest.POST("/validate", campaignRequest)
            .header("X-Tenant", "qalipsis")
            .header("Accept-Language", "en")

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                validateRequest,
                Unit::class.java
            )
        }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
            transform("body") {
                it.response.getBody(String::class.java).get()
            }.contains("Scenarios with names [Scenario1] are not exist")
        }
    }
}