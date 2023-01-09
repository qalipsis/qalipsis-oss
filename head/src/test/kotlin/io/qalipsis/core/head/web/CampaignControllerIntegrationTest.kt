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
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.query.Page
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ExecutionStatus.FAILED
import io.qalipsis.api.report.ExecutionStatus.IN_PROGRESS
import io.qalipsis.api.report.ExecutionStatus.QUEUED
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.report.ReportMessageSeverity.INFO
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignManager
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.jdbc.entity.ScenarioEntity
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.CampaignExecutionDetails
import io.qalipsis.core.head.model.Scenario
import io.qalipsis.core.head.model.ScenarioExecutionDetails
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.head.report.CampaignReportProvider
import io.qalipsis.core.head.web.handler.ErrorResponse
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import jakarta.inject.Inject
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.TRANSIENT, ExecutionEnvironments.SINGLE_HEAD])
@PropertySource(Property(name = "micronaut.server.log-handled-exceptions", value = "true"))
internal class CampaignControllerIntegrationTest {

    @Inject
    @field:Client("/campaigns")
    lateinit var httpClient: HttpClient

    @RelaxedMockK
    private lateinit var campaignManager: CampaignManager

    @RelaxedMockK
    private lateinit var campaignService: CampaignService

    @RelaxedMockK
    private lateinit var clusterFactoryService: FactoryService

    @RelaxedMockK
    private lateinit var campaignReportStateKeeper: CampaignReportStateKeeper

    @RelaxedMockK
    private lateinit var campaignReportProvider: CampaignReportProvider

    @MockBean(FactoryService::class)
    fun clusterFactoryService() = clusterFactoryService

    @MockBean(CampaignService::class)
    fun campaignService() = campaignService

    @MockBean(CampaignReportStateKeeper::class)
    fun campaignReportStateKeeper() = campaignReportStateKeeper

    @MockBean(CampaignReportProvider::class)
    fun campaignReportProvider() = campaignReportProvider

    @MockBean(CampaignManager::class)
    fun campaignManager() = campaignManager

    @BeforeEach
    internal fun setUp() {
        excludeRecords {
            campaignManager.hashCode()
            campaignService.hashCode()
        }
    }

    @Test
    fun `should successfully start valid campaign`() {
        // given
        val campaignConfiguration = CampaignConfiguration(
            name = "This is a campaign",
            scenarios = mapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )
        val runningCampaign = relaxedMockk<RunningCampaign> {
            every { key } returns "my-campaign"
        }
        coEvery {
            campaignManager.start(
                Defaults.TENANT,
                Defaults.USER,
                eq(campaignConfiguration)
            )
        } returns runningCampaign
        val createdCampaign = Campaign(
            creation = Instant.now(),
            version = Instant.now(),
            key = RandomStringUtils.randomAlphanumeric(10),
            name = "This is a campaign",
            speedFactor = 1.0,
            start = Instant.now(),
            scheduledMinions = 123,
            end = null,
            configurerName = Defaults.USER,
            aborterName = Defaults.USER,
            status = IN_PROGRESS,
            scenarios = listOf(
                Scenario(version = Instant.now().minusSeconds(3), name = "scenario-1", minionsCount = 2534),
                Scenario(version = Instant.now().minusSeconds(21312), name = "scenario-2", minionsCount = 45645)
            )
        )
        coEvery { campaignService.retrieve(Defaults.TENANT, "my-campaign") } returns createdCampaign

        // when
        val executeRequest = HttpRequest.POST("/", campaignConfiguration)
        val response = httpClient.toBlocking().exchange(
            executeRequest,
            Campaign::class.java
        )

        // then
        coVerifyOrder {
            // Called with the default user.
            campaignManager.start(Defaults.TENANT, Defaults.USER, eq(campaignConfiguration))
            campaignService.retrieve(Defaults.TENANT, "my-campaign")
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(createdCampaign)
        }
        confirmVerified(campaignService, campaignManager)
    }

    @Test
    fun `should fail when starting campaign with invalid configuration`() {
        // given
        val campaignConfiguration = CampaignConfiguration(
            name = "ju",
            scenarios = mapOf("Scenario1" to ScenarioRequest(5))
        )
        val executeRequest = HttpRequest.POST("/", campaignConfiguration)

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                executeRequest,
                CampaignConfiguration::class.java
            )
        }

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
            transform("body") {
                it.response.getBody(String::class.java).get()
            }.isEqualTo("""{"errors":[{"property":"campaign.name","message":"size must be between 3 and 300"}]}""")
        }
        confirmVerified(campaignService, campaignManager)
    }

    @Test
    fun `should successfully validate valid campaign`() {
        // given
        val campaignConfiguration = CampaignConfiguration(
            name = "just",
            scenarios = mapOf("Scenario1" to ScenarioRequest(5))
        )
        val validateRequest = HttpRequest.POST("/validate", campaignConfiguration)
            .header("Accept-Language", "en")

        coEvery { clusterFactoryService.getActiveScenarios(Defaults.TENANT, any()) } returns listOf(
            ScenarioEntity(555, "scenario-1", 500)
        ).map(ScenarioEntity::toModel)

        // when
        val response = httpClient.toBlocking().exchange(validateRequest, Unit::class.java)

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.NO_CONTENT)
        }
        confirmVerified(campaignService, campaignManager)
    }

    @Test
    fun `should fail when validating campaign with unexisting scenario`() {
        // given
        val campaignConfiguration = CampaignConfiguration(
            name = "just",
            scenarios = mapOf("Scenario1" to ScenarioRequest(5))
        )
        coEvery { clusterFactoryService.getActiveScenarios(Defaults.TENANT, any()) } returns emptyList()
        val validateRequest = HttpRequest.POST("/validate", campaignConfiguration)
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
            }.contains("Scenarios with names Scenario1 are unknown or currently disabled")
        }
        confirmVerified(campaignService, campaignManager)
    }

    @Test
    fun `should return page of campaigns with default sorting`() {
        // given
        val campaign = Campaign(
            creation = Instant.now(),
            version = Instant.now(),
            key = "campaign-1",
            name = "The campaign",
            speedFactor = 1.0,
            scheduledMinions = 123,
            start = Instant.now(),
            end = Instant.now().plusSeconds(1000),
            status = ExecutionStatus.SUCCESSFUL,
            configurerName = Defaults.USER,
            scenarios = listOf(
                Scenario(version = Instant.now().minusSeconds(3), name = "scenario-1", minionsCount = 2534),
                Scenario(version = Instant.now().minusSeconds(21312), name = "scenario-2", minionsCount = 45645)
            )
        )
        val listsRequest = HttpRequest.GET<Page<Campaign>>("/")

        coEvery {
            campaignService.search(
                Defaults.TENANT,
                emptyList(),
                "creation:desc",
                0,
                20
            )
        } returns Page(0, 1, 1, listOf(campaign))

        // when
        val response = httpClient.toBlocking().exchange(
            listsRequest, Argument.of(Page::class.java, Campaign::class.java)
        )

        //then
        coVerifyOnce { campaignService.search(Defaults.TENANT, emptyList(), "creation:desc", 0, 20) }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(Page(0, 1, 1, listOf(campaign)))
        }
        confirmVerified(campaignService, campaignManager)
    }

    @Test
    fun `should return page of campaigns with filter and default sorting`() {
        // given
        val campaign = Campaign(
            creation = Instant.now(),
            version = Instant.now(),
            key = "campaign-1",
            name = "The campaign",
            speedFactor = 1.0,
            scheduledMinions = 123,
            start = Instant.now(),
            end = Instant.now().plusSeconds(1000),
            status = ExecutionStatus.SUCCESSFUL,
            configurerName = Defaults.USER,
            scenarios = listOf(
                Scenario(version = Instant.now().minusSeconds(3), name = "scenario-1", minionsCount = 2534),
                Scenario(version = Instant.now().minusSeconds(21312), name = "scenario-2", minionsCount = 45645)
            )
        )

        val listsRequest = HttpRequest.GET<Page<Campaign>>("/?filter=an*,other")
        coEvery {
            campaignService.search(
                Defaults.TENANT,
                listOf("an*", "other"),
                "creation:desc",
                0,
                20
            )
        } returns Page(0, 1, 1, listOf(campaign))

        // when
        val response = httpClient.toBlocking().exchange(
            listsRequest, Argument.of(Page::class.java, Campaign::class.java)
        )

        //then
        coVerifyOnce { campaignService.search(Defaults.TENANT, listOf("an*", "other"), "creation:desc", 0, 20) }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(Page(0, 1, 1, listOf(campaign)))
        }
        confirmVerified(campaignService, campaignManager)
    }

    @Test
    fun `should return page of campaigns with filter and sort`() {
        // given
        val campaign = Campaign(
            creation = Instant.now(),
            version = Instant.now(),
            key = "campaign-1",
            name = "The campaign",
            speedFactor = 1.0,
            scheduledMinions = 123,
            start = Instant.now(),
            end = Instant.now().plusSeconds(1000),
            status = ExecutionStatus.SUCCESSFUL,
            configurerName = Defaults.USER,
            aborterName = Defaults.USER,
            scenarios = listOf(
                Scenario(version = Instant.now().minusSeconds(3), name = "scenario-1", minionsCount = 2534),
                Scenario(version = Instant.now().minusSeconds(21312), name = "scenario-2", minionsCount = 45645)
            )
        )

        val listsRequest =
            HttpRequest.GET<Page<Campaign>>("/?filter=campaign&sort=name")

        coEvery {
            campaignService.search(
                Defaults.TENANT,
                listOf("campaign"),
                "name",
                0,
                20
            )
        } returns Page(0, 1, 1, listOf(campaign))

        // when
        val response = httpClient.toBlocking().exchange(
            listsRequest, Argument.of(Page::class.java, Campaign::class.java)
        )

        // then
        coVerifyOnce { campaignService.search(Defaults.TENANT, listOf("campaign"), "name", 0, 20) }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(Page(0, 1, 1, listOf(campaign)))
        }
        confirmVerified(campaignService, campaignManager)
    }

    @Test
    fun `should successfully abort the campaign`() {
        // given
        val abortRequest = HttpRequest.POST("/first_campaign/abort", null)

        // when
        val response = httpClient.toBlocking().exchange(abortRequest, Unit::class.java)

        // then
        coVerifyOnce {
            campaignManager.abort(Defaults.TENANT, Defaults.USER, "first_campaign", false)
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.ACCEPTED)
        }
        confirmVerified(campaignService, campaignManager)
    }

    @Test
    fun `should successfully abort the campaign hard`() {
        // given
        val abortRequest = HttpRequest.POST("/first_campaign/abort?hard=true", null)

        // when
        val response = httpClient.toBlocking().exchange(abortRequest, Unit::class.java)

        // then
        coVerifyOnce {
            campaignManager.abort(Defaults.TENANT, Defaults.USER, "first_campaign", true)
        }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.ACCEPTED)
        }
        confirmVerified(campaignService, campaignManager)
    }

    @Test
    fun `should successfully retrieve the campaign report per tenant`() {
        // given
        val campaignExecutionDetails =
            CampaignExecutionDetails(
                creation = Instant.now(),
                version = Instant.now(),
                key = RandomStringUtils.randomAlphanumeric(10),
                name = "This is a campaign",
                speedFactor = 1.0,
                start = Instant.now(),
                scheduledMinions = 123,
                end = null,
                configurerName = Defaults.USER,
                status = ExecutionStatus.SUCCESSFUL,
                scenarios = listOf(
                    Scenario(version = Instant.now().minusSeconds(3), name = "scenario-1", minionsCount = 2534),
                    Scenario(version = Instant.now().minusSeconds(21312), name = "scenario-2", minionsCount = 45645)
                ),
                startedMinions = 0,
                completedMinions = 0,
                successfulExecutions = 0,
                failedExecutions = 0,
                scenariosReports = listOf(
                    ScenarioExecutionDetails(
                        id = "my-scenario-1",
                        name = "The scenario 1",
                        start = Instant.now().minusMillis(1111),
                        end = Instant.now(),
                        startedMinions = 0,
                        completedMinions = 0,
                        successfulExecutions = 0,
                        failedExecutions = 0,
                        status = FAILED,
                        messages = listOf(
                            ReportMessage(
                                stepName = "my-step-1",
                                messageId = "message-id-1",
                                severity = INFO,
                                message = "Hello from test 1"
                            )
                        )
                    ),
                    ScenarioExecutionDetails(
                        id = "my-scenario-2",
                        name = "The scenario 2",
                        start = Instant.now().minusMillis(1111),
                        end = Instant.now(),
                        startedMinions = 1,
                        completedMinions = 1,
                        successfulExecutions = 1,
                        failedExecutions = 1,
                        status = ExecutionStatus.ABORTED,
                        messages = listOf(
                            ReportMessage(
                                stepName = "my-step-2",
                                messageId = "message-id-2",
                                severity = ReportMessageSeverity.INFO,
                                message = "Hello from test 2"
                            )
                        )
                    )
                )
            )

        val getCampaignReportRequest = HttpRequest.GET<CampaignReport>("/first_campaign/")
        coEvery {
            campaignReportProvider.retrieveCampaignReport(Defaults.TENANT, "first_campaign")
        } returns campaignExecutionDetails

        // when
        val response = httpClient.toBlocking().exchange(getCampaignReportRequest, CampaignExecutionDetails::class.java)

        // then
        coVerifyOnce {
            campaignReportProvider.retrieveCampaignReport(Defaults.TENANT, "first_campaign")
        }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(campaignExecutionDetails)
        }
        confirmVerified(campaignService, campaignManager)
    }

    @Test
    fun `should successfully replay the campaign`() {
        // given
        val campaign = Campaign(
            creation = Instant.now(),
            version = Instant.now(),
            key = RandomStringUtils.randomAlphanumeric(10),
            name = "This is a campaign",
            speedFactor = 1.0,
            start = Instant.now(),
            scheduledMinions = 123,
            end = null,
            configurerName = Defaults.USER,
            status = QUEUED,
            scenarios = listOf(
                Scenario(version = Instant.now().minusSeconds(3), name = "scenario-1", minionsCount = 2534),
                Scenario(version = Instant.now().minusSeconds(21312), name = "scenario-2", minionsCount = 45645)
            )
        )
        val runningCampaign = RunningCampaign(tenant = "my-tenant", key = "my-campaign-new")
        val replayRequest = HttpRequest.POST("/my-campaign/replay", null)
        coEvery {
            campaignManager.replay(Defaults.TENANT, Defaults.USER, "my-campaign")
        } returns runningCampaign
        coEvery {
            campaignService.retrieve(Defaults.TENANT, "my-campaign-new")
        } returns campaign

        // when
        val response = httpClient.toBlocking().exchange(replayRequest, Campaign::class.java)

        // then
        coVerifyOnce {
            campaignManager.replay(Defaults.TENANT, Defaults.USER, "my-campaign")
            campaignService.retrieve(Defaults.TENANT, "my-campaign-new")
        }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(campaign)
        }
        confirmVerified(campaignService, campaignManager)
    }

    @Test
    fun `should successfully retrieve the configuration when it exists`() {
        // given
        val campaignConfiguration = CampaignConfiguration(
            name = "This is a campaign",
            scenarios = mapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )
        val retrieveConfigurationRequest = HttpRequest.GET<CampaignConfiguration>("/my-campaign/configuration")
        coEvery {
            campaignService.retrieveConfiguration(Defaults.TENANT, "my-campaign")
        } returns campaignConfiguration

        // when
        val response = httpClient.toBlocking().exchange(retrieveConfigurationRequest, CampaignConfiguration::class.java)

        // then
        coVerifyOnce {
            campaignService.retrieveConfiguration(Defaults.TENANT, "my-campaign")
        }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(campaignConfiguration)
        }
        confirmVerified(campaignService, campaignManager)
    }

    @Test
    fun `should fail to retrieve the configuration when it does not exist`() {
        // given
        coEvery {
            campaignService.retrieveConfiguration(Defaults.TENANT, "my-campaign")
        } throws IllegalArgumentException("The configuration does not exists")
        val retrieveConfigurationRequest = HttpRequest.GET<CampaignConfiguration>("/my-campaign/configuration")

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(retrieveConfigurationRequest, CampaignConfiguration::class.java)
        }.response

        // then
        coVerifyOnce {
            campaignService.retrieveConfiguration(Defaults.TENANT, "my-campaign")
        }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
            transform("body") { it.getBody(ErrorResponse::class.java).get() }.prop(ErrorResponse::errors)
                .containsOnly("The configuration does not exists")
        }
        confirmVerified(campaignService, campaignManager)
    }
}