package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignManager
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.jdbc.entity.ScenarioEntity
import io.qalipsis.core.head.web.model.CampaignConfigurationConverter
import io.qalipsis.core.head.web.model.CampaignRequest
import io.qalipsis.core.head.web.model.ScenarioRequest
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.VOLATILE, ExecutionEnvironments.SINGLE_HEAD])
@PropertySource(
    Property(name = "micronaut.server.log-handled-exceptions", value = "true"),
    Property(name = "identity.bind-tenant", value = "true")
)
internal class CampaignControllerIntegrationTest {

    @Inject
    @field:Client("/campaigns")
    lateinit var httpClient: HttpClient

    @RelaxedMockK
    private lateinit var campaignManager: CampaignManager

    @RelaxedMockK
    private lateinit var clusterFactoryService: FactoryService

    @RelaxedMockK
    private lateinit var campaignConfigurationConverter: CampaignConfigurationConverter

    @MockBean(FactoryService::class)
    fun clusterFactoryService() = clusterFactoryService

    @MockBean(CampaignManager::class)
    fun campaignManager() = campaignManager

    @MockBean(CampaignConfigurationConverter::class)
    fun campaignConfigurationConverter() = campaignConfigurationConverter

    @Test
    fun `should successfully start successful campaign`() {
        // given
        val campaignRequest = CampaignRequest(
            name = "just-test",
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )
        val executeRequest = HttpRequest.POST("/", campaignRequest).header("X-Tenant", "my-tenant")
        val campaignConfiguration = relaxedMockk<CampaignConfiguration>()
        every {
            campaignConfigurationConverter.convertRequestToConfiguration(
                any(),
                any()
            )
        } returns campaignConfiguration

        // when
        val response = httpClient.toBlocking().exchange(
            executeRequest,
            CampaignRequest::class.java
        )

        // then
        coVerifyOnce {
            campaignConfigurationConverter.convertRequestToConfiguration("my-tenant", campaignRequest)
            // Called with the default user.
            campaignManager.start("_qalipsis_", refEq(campaignConfiguration))
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.ACCEPTED)
        }
    }

    @Test
    fun `should fail when starting campaign with invalid configuration`() {
        // given
        val campaignRequest = CampaignRequest(
            name = "ju",
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(5))
        )
        val executeRequest = HttpRequest.POST("/", campaignRequest).header("X-Tenant", "my-tenant")

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
    fun `should successfully validate valid campaign`() {
        // given
        val campaignRequest = CampaignRequest(
            name = "just",
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(5))
        )
        val validateRequest = HttpRequest.POST("/validate", campaignRequest)
            .header("X-Tenant", "my-tenant")
            .header("Accept-Language", "en")
        coEvery { clusterFactoryService.getActiveScenarios("my-tenant", any()) } returns listOf(
            ScenarioEntity(555, "scenario-1", 500)
        ).map(ScenarioEntity::toModel)

        // when
        val response = httpClient.toBlocking().exchange(validateRequest, Unit::class.java)

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.ACCEPTED)
        }
    }

    @Test
    fun `should fail when validating campaign with unexisting scenario`() {
        // given
        val campaignRequest = CampaignRequest(
            name = "just",
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(5))
        )
        coEvery { clusterFactoryService.getActiveScenarios("my-tenant", any()) } returns emptyList()
        val validateRequest = HttpRequest.POST("/validate", campaignRequest)
            .header("X-Tenant", "my-tenant")
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