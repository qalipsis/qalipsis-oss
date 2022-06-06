package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isDataClassEqualTo
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
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignManager
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.jdbc.entity.ScenarioEntity
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignRequest
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.core.head.model.converter.CampaignConverter
import io.qalipsis.core.head.security.Permissions
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.relaxedMockk
import jakarta.inject.Inject
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.VOLATILE, ExecutionEnvironments.SINGLE_HEAD, "jwt"])
@PropertySource(Property(name = "micronaut.server.log-handled-exceptions", value = "true"))
internal class CampaignControllerIntegrationTest {

    @Inject
    @field:Client("/campaigns")
    lateinit var httpClient: HttpClient

    @Inject
    private lateinit var jwtGenerator: JwtGenerator

    @RelaxedMockK
    private lateinit var campaignManager: CampaignManager

    @RelaxedMockK
    private lateinit var campaignService: CampaignService

    @RelaxedMockK
    private lateinit var clusterFactoryService: FactoryService

    @RelaxedMockK
    private lateinit var campaignConverter: CampaignConverter

    @MockBean(FactoryService::class)
    fun clusterFactoryService() = clusterFactoryService

    @MockBean(CampaignService::class)
    fun campaignService() = campaignService

    @MockBean(CampaignManager::class)
    fun campaignManager() = campaignManager

    @MockBean(CampaignConverter::class)
    fun campaignConfigurationConverter() = campaignConverter

    @Test
    fun `should successfully start valid campaign`() {
        // given
        val campaignRequest = CampaignRequest(
            name = "This is a campaign",
            scenarios = mapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )
        val campaignConfiguration = relaxedMockk<CampaignConfiguration>()
        coEvery { campaignConverter.convertRequest(any(), any()) } returns campaignConfiguration
        val createdCampaign = Campaign(
            version = Instant.now(),
            key = RandomStringUtils.randomAlphanumeric(10),
            name = "This is a campaign",
            speedFactor = 1.0,
            start = Instant.now(),
            end = null,
            configurerName = "my-user",
            result = null
        )
        coEvery {
            campaignManager.start(
                "my-user",
                "This is a campaign",
                refEq(campaignConfiguration)
            )
        } returns createdCampaign

        // when
        val executeRequest = HttpRequest.POST("/", campaignRequest)
            .header("X-Tenant", "my-tenant")
            .bearerAuth(jwtGenerator.generateValidToken("my-user", listOf(Permissions.CREATE_CAMPAIGN)))

        val response = httpClient.toBlocking().exchange(
            executeRequest,
            Campaign::class.java
        )

        // then
        coVerifyOrder {
            campaignConverter.convertRequest("my-tenant", campaignRequest)
            // Called with the default user.
            campaignManager.start("my-user", "This is a campaign", refEq(campaignConfiguration))
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(createdCampaign)
        }
    }

    @Test
    fun `should fail when starting campaign with invalid configuration`() {
        // given
        val campaignRequest = CampaignRequest(
            name = "ju",
            scenarios = mapOf("Scenario1" to ScenarioRequest(5))
        )
        val executeRequest = HttpRequest.POST("/", campaignRequest)
            .header("X-Tenant", "my-tenant")
            .bearerAuth(jwtGenerator.generateValidToken("my-user", listOf(Permissions.CREATE_CAMPAIGN)))

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
            scenarios = mapOf("Scenario1" to ScenarioRequest(5))
        )
        val validateRequest = HttpRequest.POST("/validate", campaignRequest)
            .header("Accept-Language", "en")
            .header("X-Tenant", "my-tenant")
            .bearerAuth(jwtGenerator.generateValidToken("my-user", listOf(Permissions.CREATE_CAMPAIGN)))

        coEvery { clusterFactoryService.getActiveScenarios("my-tenant", any()) } returns listOf(
            ScenarioEntity(555, "scenario-1", 500)
        ).map(ScenarioEntity::toModel)

        // when
        val response = httpClient.toBlocking().exchange(validateRequest, Unit::class.java)

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.NO_CONTENT)
        }
    }

    @Test
    fun `should fail when validating campaign with unexisting scenario`() {
        // given
        val campaignRequest = CampaignRequest(
            name = "just",
            scenarios = mapOf("Scenario1" to ScenarioRequest(5))
        )
        coEvery { clusterFactoryService.getActiveScenarios("my-tenant", any()) } returns emptyList()
        val validateRequest = HttpRequest.POST("/validate", campaignRequest)
            .header("Accept-Language", "en")
            .header("X-Tenant", "my-tenant")
            .bearerAuth(jwtGenerator.generateValidToken("my-user", listOf(Permissions.CREATE_CAMPAIGN)))

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
            }.contains("Scenarios with names Scenario1 are unknown or currently disabled")
        }
    }

    @Test
    fun `should deny starting campaign when the permission is missing`() {
        // given
        val campaignRequest = CampaignRequest(
            name = "This is a campaign",
            scenarios = mapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )

        val executeRequest = HttpRequest.POST("/", campaignRequest)
            .header("X-Tenant", "my-tenant")
            .bearerAuth(jwtGenerator.generateValidToken("my-user"))

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                executeRequest,
                Campaign::class.java
            )
        }

        // then
        assertThat(response).transform("statusCode") { it.status }.isEqualTo(HttpStatus.FORBIDDEN)
        coVerifyNever { campaignManager.start(any(), any(), any()) }
    }

    @Test
    fun `should deny validating campaign when the permission is missing`() {
        // given
        val campaignRequest = CampaignRequest(
            name = "just",
            scenarios = mapOf("Scenario1" to ScenarioRequest(5))
        )
        val validateRequest = HttpRequest.POST("/validate", campaignRequest)
            .header("Accept-Language", "en")
            .header("X-Tenant", "my-tenant")
            .bearerAuth(jwtGenerator.generateValidToken("my-user"))

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                validateRequest,
                Campaign::class.java
            )
        }

        // then
        assertThat(response).transform("statusCode") { it.status }.isEqualTo(HttpStatus.FORBIDDEN)
        coVerifyNever { clusterFactoryService.getActiveScenarios(any(), any()) }
    }

}