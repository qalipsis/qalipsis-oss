package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import io.micronaut.core.type.Argument
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
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.factory.ClusterFactoryService
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
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
    @field:Client("/")
    lateinit var httpClient: HttpClient

    @RelaxedMockK
    private lateinit var campaignManager: CampaignManager

    @RelaxedMockK
    private lateinit var campaignService: CampaignService

    @RelaxedMockK
    private lateinit var clusterFactoryService: ClusterFactoryService

    @RelaxedMockK
    private lateinit var campaignConfigurationConverter: CampaignConfigurationConverter

    @MockBean(ClusterFactoryService::class)
    fun clusterFactoryService() = clusterFactoryService

    @MockBean(CampaignService::class)
    fun campaignService() = campaignService

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
        val executeRequest = HttpRequest.POST("/campaigns", campaignRequest).header("X-Tenant", "qalipsis")

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
        val executeRequest = HttpRequest.POST("/campaigns", campaignRequest).header("X-Tenant", "qalipsis")

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
        val validateRequest = HttpRequest.POST("/campaigns/validate", campaignRequest)
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
        val validateRequest = HttpRequest.POST("/campaigns/validate", campaignRequest)
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

    @Test
    fun `should return list of campaigns`() {
        // given
        val campaign = CampaignEntity(campaignName = "campaign-1", configurer = 1)
        val listsRequest = HttpRequest.GET<List<CampaignEntity>>("/campaigns")
            .header("X-Tenant", "my-tenant")

        coEvery { campaignService.getAllCampaigns("my-tenant", null, null) } returns listOf(campaign)

        // when
        val response = httpClient.toBlocking().exchange(listsRequest, Argument.listOf(CampaignEntity::class.java))

        //then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.all {
                hasSize(1)
                index(0).isDataClassEqualTo(campaign)
            }
        }
    }

    @Test
    fun `should return list of campaigns with filter`() {
        // given
        val campaign = CampaignEntity(campaignName = "campaign-1", configurer = 1)

        val listsRequest = HttpRequest.GET<List<CampaignEntity>>("/campaigns?filter=campaign-1")
            .header("X-Tenant", "my-tenant")
        coEvery { campaignService.getAllCampaigns("my-tenant", "campaign-1", null) } returns listOf(campaign)


        // when
        val response = httpClient.toBlocking().exchange(listsRequest, Argument.listOf(CampaignEntity::class.java))

        //then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.all {
                hasSize(1)
                index(0).isDataClassEqualTo(campaign)
            }
        }
    }

    @Test
    fun `should return list of campaigns with filter and sort`() {
        // given
        val campaign = CampaignEntity(campaignName = "campaign-1", configurer = 1)

        val listsRequest = HttpRequest.GET<List<CampaignEntity>>("/campaigns?filter=campaign-1&sort=name")
            .header("X-Tenant", "my-tenant")

        coEvery { campaignService.getAllCampaigns("my-tenant", "campaign-1", "name") } returns listOf(campaign)

        // when
        val response = httpClient.toBlocking().exchange(listsRequest, Argument.listOf(CampaignEntity::class.java))

        //then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.all {
                hasSize(1)
                index(0).isDataClassEqualTo(campaign)
            }
        }
    }
}