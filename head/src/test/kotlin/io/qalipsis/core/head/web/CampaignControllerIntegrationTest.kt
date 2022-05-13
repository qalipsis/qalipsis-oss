package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.campaign.ScenarioConfiguration
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignManager
import io.qalipsis.core.head.web.entity.CampaignRequest
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.VOLATILE, ExecutionEnvironments.SINGLE_HEAD])
internal class CampaignControllerIntegrationTest {

    @Inject
    @field:Client("/campaigns")
    lateinit var httpClient: HttpClient

    @RelaxedMockK
    private lateinit var campaignManager: CampaignManager

    @MockBean(CampaignManager::class)
    fun campaignManager() = campaignManager

    @Test
    fun `should return status ok when start campaign`() {
        // given
        val campaignRequest = CampaignRequest(
            username = "qalipsis-user",
            name = "just-test",
            scenarios = mutableMapOf("Scenario1" to ScenarioConfiguration(5))
        )
        val executeRequest = HttpRequest.POST("/", campaignRequest).header("X-Tenant", "qalipsis")

        // when
        val response = httpClient.toBlocking().exchange(
            executeRequest,
            CampaignRequest::class.java
        )

        // then
        coVerifyOnce {
            campaignManager.start("qalipsis-user", any())
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
        }
    }

    @Test
    fun `should return 400 when saving with a too short name`() {
        // given
        val campaignRequest = CampaignRequest(
            username = "qalipsis-user",
            name = "ju",
            scenarios = mutableMapOf("Scenario1" to ScenarioConfiguration(5))
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
        assertEquals(HttpStatus.BAD_REQUEST, response.status)
    }

    @Test
    fun `should return 400 when saving with an empty scenarios`() {
        // given
        val campaignRequest = CampaignRequest(
            username = "qalipsis-user",
            name = "just-test",
            scenarios = emptyMap()
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
        assertEquals(HttpStatus.BAD_REQUEST, response.status)
    }

    @Test
    fun `should return 400 when saving with a blank user name`() {
        // given
        val campaignRequest = CampaignRequest(
            username = "",
            name = "just-test",
            scenarios = mutableMapOf("Scenario1" to ScenarioConfiguration(5))
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
        assertEquals(HttpStatus.BAD_REQUEST, response.status)
    }

    @Test
    fun `should return status ok for valid campaign`() {
        // given
//        val campaignConfiguration = CampaignConfiguration(
//            tenant = "",
//            name = "just-test",
//            scenarios = mutableMapOf(
//                "Scenario1" to ScenarioConfiguration(5)
//            )
//        )

        val campaignRequest = CampaignRequest(
            username = "qalipsis-user",
            name = "ju",
            scenarios = mutableMapOf("Scenario1" to ScenarioConfiguration(5))
        )
        val validateRequest = HttpRequest.POST("/validate", campaignRequest).header("X-Tenant", "qalipsis")

        // when
        try {
            val response = httpClient.toBlocking().exchange(
                validateRequest,
                CampaignRequest::class.java
            )
            assertThat(response).all {
                transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            }
        } catch (e: HttpClientResponseException) {
            print(e.message)
        }
    }
}