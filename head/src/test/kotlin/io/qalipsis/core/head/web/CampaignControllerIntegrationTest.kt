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
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ExecutionStatus.FAILED
import io.qalipsis.api.report.ExecutionStatus.IN_PROGRESS
import io.qalipsis.api.report.ExecutionStatus.QUEUED
import io.qalipsis.api.report.ExecutionStatus.SUCCESSFUL
import io.qalipsis.api.report.ExecutionStatus.WARNING
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity.INFO
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignExecutor
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.campaign.scheduler.CampaignScheduler
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
    private lateinit var campaignExecutor: CampaignExecutor

    @RelaxedMockK
    private lateinit var campaignService: CampaignService

    @RelaxedMockK
    private lateinit var clusterFactoryService: FactoryService

    @RelaxedMockK
    private lateinit var campaignReportStateKeeper: CampaignReportStateKeeper

    @RelaxedMockK
    private lateinit var campaignReportProvider: CampaignReportProvider

    @RelaxedMockK
    private lateinit var campaignScheduler: CampaignScheduler

    @MockBean(FactoryService::class)
    fun clusterFactoryService() = clusterFactoryService

    @MockBean(CampaignService::class)
    fun campaignService() = campaignService

    @MockBean(CampaignReportStateKeeper::class)
    fun campaignReportStateKeeper() = campaignReportStateKeeper

    @MockBean(CampaignReportProvider::class)
    fun campaignReportProvider() = campaignReportProvider

    @MockBean(CampaignExecutor::class)
    fun campaignExecutor() = campaignExecutor

    @MockBean(CampaignScheduler::class)
    fun campaignScheduler() = campaignScheduler

    @BeforeEach
    fun setUp() {
        excludeRecords {
            campaignExecutor.hashCode()
            campaignService.hashCode()
            campaignScheduler.hashCode()
            clusterFactoryService.hashCode()
            campaignReportProvider.hashCode()
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
            campaignExecutor.start(
                Defaults.TENANT,
                Defaults.USERNAME,
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
            configurerName = Defaults.USERNAME,
            aborterName = Defaults.USERNAME,
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
            campaignExecutor.start(Defaults.TENANT, Defaults.USERNAME, eq(campaignConfiguration))
            campaignService.retrieve(Defaults.TENANT, "my-campaign")
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(createdCampaign)
        }
        confirmVerified(
            campaignService,
            campaignExecutor,
            campaignReportProvider,
            campaignReportStateKeeper,
            clusterFactoryService,
            campaignScheduler
        )
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
        confirmVerified(
            campaignService,
            campaignExecutor,
            campaignReportProvider,
            campaignReportStateKeeper,
            clusterFactoryService,
            campaignScheduler
        )
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
            ScenarioEntity(555, "scenario-1", "my-scenario", "0.1", Instant.now(), 500)
        ).map(ScenarioEntity::toModel)

        // when
        val response = httpClient.toBlocking().exchange(validateRequest, Unit::class.java)

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.NO_CONTENT)
        }
        coVerifyOrder {
            clusterFactoryService.getActiveScenarios(Defaults.TENANT, any())
        }
        confirmVerified(
            campaignService,
            campaignExecutor,
            campaignReportProvider,
            campaignReportStateKeeper,
            clusterFactoryService,
            campaignScheduler
        )
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
        coVerifyOrder {
            clusterFactoryService.getActiveScenarios(Defaults.TENANT, any())
        }
        confirmVerified(
            campaignService,
            campaignExecutor,
            campaignReportProvider,
            campaignReportStateKeeper,
            clusterFactoryService,
            campaignScheduler
        )
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
            configurerName = Defaults.USERNAME,
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
                "start:desc",
                0,
                20,
                emptyList()
            )
        } returns Page(0, 1, 1, listOf(campaign))

        // when
        val response = httpClient.toBlocking().exchange(
            listsRequest, Argument.of(Page::class.java, Campaign::class.java)
        )

        //then
        coVerifyOnce { campaignService.search(Defaults.TENANT, emptyList(), "start:desc", 0, 20, emptyList()) }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(Page(0, 1, 1, listOf(campaign)))
        }
        confirmVerified(
            campaignService,
            campaignExecutor,
            campaignReportProvider,
            campaignReportStateKeeper,
            clusterFactoryService,
            campaignScheduler
        )
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
            status = SUCCESSFUL,
            configurerName = Defaults.USERNAME,
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
                "start:desc",
                0,
                20,
                emptyList()
            )
        } returns Page(0, 1, 1, listOf(campaign))

        // when
        val response = httpClient.toBlocking().exchange(
            listsRequest, Argument.of(Page::class.java, Campaign::class.java)
        )

        //then
        coVerifyOnce { campaignService.search(Defaults.TENANT, listOf("an*", "other"), "start:desc", 0, 20, emptyList()) }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(Page(0, 1, 1, listOf(campaign)))
        }
        confirmVerified(
            campaignService,
            campaignExecutor,
            campaignReportProvider,
            campaignReportStateKeeper,
            clusterFactoryService,
            campaignScheduler
        )
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
            status = SUCCESSFUL,
            configurerName = Defaults.USERNAME,
            aborterName = Defaults.USERNAME,
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
                20,
                emptyList()
            )
        } returns Page(0, 1, 1, listOf(campaign))

        // when
        val response = httpClient.toBlocking().exchange(
            listsRequest, Argument.of(Page::class.java, Campaign::class.java)
        )

        // then
        coVerifyOnce { campaignService.search(Defaults.TENANT, listOf("campaign"), "name", 0, 20, emptyList()) }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(Page(0, 1, 1, listOf(campaign)))
        }
        confirmVerified(
            campaignService,
            campaignExecutor,
            campaignReportProvider,
            campaignReportStateKeeper,
            clusterFactoryService,
            campaignScheduler
        )
    }

    @Test
    fun `should successfully abort the campaign`() {
        // given
        val abortRequest = HttpRequest.POST("/first_campaign/abort", null)

        // when
        val response = httpClient.toBlocking().exchange(abortRequest, Unit::class.java)

        // then
        coVerifyOnce {
            campaignExecutor.abort(Defaults.TENANT, Defaults.USERNAME, "first_campaign", false)
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.ACCEPTED)
        }
        confirmVerified(
            campaignService,
            campaignExecutor,
            campaignReportProvider,
            campaignReportStateKeeper,
            clusterFactoryService,
            campaignScheduler
        )
    }

    @Test
    fun `should successfully abort the campaign hard`() {
        // given
        val abortRequest = HttpRequest.POST("/first_campaign/abort?hard=true", null)

        // when
        val response = httpClient.toBlocking().exchange(abortRequest, Unit::class.java)

        // then
        coVerifyOnce {
            campaignExecutor.abort(Defaults.TENANT, Defaults.USERNAME, "first_campaign", true)
        }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.ACCEPTED)
        }
        confirmVerified(
            campaignService,
            campaignExecutor,
            campaignReportProvider,
            campaignReportStateKeeper,
            clusterFactoryService,
            campaignScheduler
        )
    }

    @Test
    fun `should successfully retrieve the report of one campaign in a tenant`() {
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
                configurerName = Defaults.USERNAME,
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
                                severity = INFO,
                                message = "Hello from test 2"
                            )
                        )
                    )
                )
            )

        val getCampaignReportRequest = HttpRequest.GET<CampaignExecutionDetails>("/key-1/")
        coEvery {
            campaignReportProvider.retrieveCampaignsReports(Defaults.TENANT, listOf("key-1"))
        } returns listOf(campaignExecutionDetails)

        // when
        val response = httpClient.toBlocking()
            .exchange(getCampaignReportRequest, Argument.listOf(CampaignExecutionDetails::class.java))

        // then
        coVerifyOnce {
            campaignReportProvider.retrieveCampaignsReports(Defaults.TENANT, listOf("key-1"))
        }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(listOf(campaignExecutionDetails))
        }
        confirmVerified(
            campaignService,
            campaignExecutor,
            campaignReportProvider,
            campaignReportStateKeeper,
            clusterFactoryService,
            campaignScheduler
        )
    }

    @Test
    fun `should successfully retrieve the report of multiples campaigns in a tenant`() {
        // given
        val campaignExecutionDetails =
            CampaignExecutionDetails(
                creation = Instant.now(),
                version = Instant.now(),
                key = "key-1",
                name = "This is a campaign",
                speedFactor = 1.0,
                start = Instant.now(),
                scheduledMinions = 123,
                end = null,
                configurerName = Defaults.USERNAME,
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
                                severity = INFO,
                                message = "Hello from test 2"
                            )
                        )
                    )
                )
            )
        val campaignExecutionDetails2 =
            CampaignExecutionDetails(
                creation = Instant.now(),
                version = Instant.now(),
                key = "key-2",
                name = "This is a campaign",
                speedFactor = 1.0,
                start = Instant.now(),
                scheduledMinions = 123,
                end = null,
                configurerName = Defaults.USERNAME,
                status = ExecutionStatus.SUCCESSFUL,
                scenarios = listOf(
                    Scenario(version = Instant.now().minusSeconds(3), name = "scenario-1", minionsCount = 2534),
                    Scenario(version = Instant.now().minusSeconds(21312), name = "scenario-2", minionsCount = 45645)
                ),
                startedMinions = 0,
                completedMinions = 0,
                successfulExecutions = 0,
                failedExecutions = 0,
                scenariosReports = listOf()
            )
        val campaignExecutionDetails3 =
            CampaignExecutionDetails(
                creation = Instant.now(),
                version = Instant.now(),
                key = "key-3",
                name = "This is a campaign",
                speedFactor = 1.0,
                start = Instant.now(),
                scheduledMinions = 123,
                end = null,
                configurerName = Defaults.USERNAME,
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
                    )
                )
            )
        val campaignExecutionDetails4 =
            CampaignExecutionDetails(
                creation = Instant.now(),
                version = Instant.now(),
                key = "key-4",
                name = "This is a campaign",
                speedFactor = 1.0,
                start = Instant.now(),
                scheduledMinions = 123,
                end = null,
                configurerName = Defaults.USERNAME,
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
                                severity = INFO,
                                message = "Hello from test 2"
                            )
                        )
                    )
                )
            )

        val getCampaignReportRequest = HttpRequest.GET<Collection<CampaignExecutionDetails>>("/key-1,key-3,key-4,key-2")
        coEvery {
            campaignReportProvider.retrieveCampaignsReports(Defaults.TENANT, listOf("key-1", "key-3", "key-4", "key-2"))
        } returns listOf(
            campaignExecutionDetails,
            campaignExecutionDetails3,
            campaignExecutionDetails4,
            campaignExecutionDetails2
        )

        // when
        val response = httpClient.toBlocking()
            .exchange(getCampaignReportRequest, Argument.listOf(CampaignExecutionDetails::class.java))

        // then
        coVerifyOnce {
            campaignReportProvider.retrieveCampaignsReports(Defaults.TENANT, listOf("key-1", "key-3", "key-4", "key-2"))
        }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(
                listOf(
                    campaignExecutionDetails,
                    campaignExecutionDetails3,
                    campaignExecutionDetails4,
                    campaignExecutionDetails2
                )
            )
        }
        confirmVerified(
            campaignService,
            campaignExecutor,
            campaignReportProvider,
            campaignReportStateKeeper,
            clusterFactoryService,
            campaignScheduler
        )
    }

    @Test
    fun `should successfully return an empty list when keys list weren't found`() {
        // given
        val getCampaignReportRequest = HttpRequest.GET<CampaignExecutionDetails>("/key-1/")
        coEvery {
            campaignReportProvider.retrieveCampaignsReports(Defaults.TENANT, listOf("key-1"))
        } returns listOf()

        // when
        val response = httpClient.toBlocking()
            .exchange(getCampaignReportRequest, Argument.listOf(CampaignExecutionDetails::class.java))

        // then
        coVerifyOnce {
            campaignReportProvider.retrieveCampaignsReports(Defaults.TENANT, listOf("key-1"))
        }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(listOf())
        }
        confirmVerified(
            campaignService,
            campaignExecutor,
            campaignReportProvider,
            campaignReportStateKeeper,
            clusterFactoryService,
            campaignScheduler
        )
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
            configurerName = Defaults.USERNAME,
            status = QUEUED,
            scenarios = listOf(
                Scenario(version = Instant.now().minusSeconds(3), name = "scenario-1", minionsCount = 2534),
                Scenario(version = Instant.now().minusSeconds(21312), name = "scenario-2", minionsCount = 45645)
            )
        )
        val runningCampaign = RunningCampaign(tenant = "my-tenant", key = "my-campaign-new")
        val replayRequest = HttpRequest.POST("/my-campaign/replay", null)
        coEvery {
            campaignExecutor.replay(Defaults.TENANT, Defaults.USERNAME, "my-campaign")
        } returns runningCampaign
        coEvery {
            campaignService.retrieve(Defaults.TENANT, "my-campaign-new")
        } returns campaign

        // when
        val response = httpClient.toBlocking().exchange(replayRequest, Campaign::class.java)

        // then
        coVerifyOnce {
            campaignExecutor.replay(Defaults.TENANT, Defaults.USERNAME, "my-campaign")
            campaignService.retrieve(Defaults.TENANT, "my-campaign-new")
        }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(campaign)
        }
        confirmVerified(
            campaignService,
            campaignExecutor,
            campaignReportProvider,
            campaignReportStateKeeper,
            clusterFactoryService,
            campaignScheduler
        )
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
        confirmVerified(
            campaignService,
            campaignExecutor,
            campaignReportProvider,
            campaignReportStateKeeper,
            clusterFactoryService,
            campaignScheduler
        )
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
        confirmVerified(
            campaignService,
            campaignExecutor,
            campaignReportProvider,
            campaignReportStateKeeper,
            clusterFactoryService,
            campaignScheduler
        )
    }

    @Test
    fun `should successfully schedule valid campaign`() {
        // given
        val campaignConfiguration = CampaignConfiguration(
            name = "This is a campaign",
            scenarios = mapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )
        val runningCampaign = relaxedMockk<RunningCampaign> {
            every { key } returns "my-campaign"
        }
        coEvery {
            campaignScheduler.schedule(
                Defaults.TENANT,
                Defaults.USERNAME,
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
            configurerName = Defaults.USERNAME,
            aborterName = Defaults.USERNAME,
            status = IN_PROGRESS,
            scenarios = listOf(
                Scenario(version = Instant.now().minusSeconds(3), name = "scenario-1", minionsCount = 2534),
                Scenario(version = Instant.now().minusSeconds(21312), name = "scenario-2", minionsCount = 45645)
            )
        )
        coEvery { campaignService.retrieve(Defaults.TENANT, "my-campaign") } returns createdCampaign

        // when
        val executeRequest = HttpRequest.POST("/schedule", campaignConfiguration)
        val response = httpClient.toBlocking().exchange(
            executeRequest,
            Campaign::class.java
        )

        // then
        coVerifyOrder {
            // Called with the default user.
            campaignScheduler.schedule(Defaults.TENANT, Defaults.USERNAME, eq(campaignConfiguration))
            campaignService.retrieve(Defaults.TENANT, "my-campaign")
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(createdCampaign)
        }
        confirmVerified(
            campaignService,
            campaignExecutor,
            campaignReportProvider,
            campaignReportStateKeeper,
            clusterFactoryService,
            campaignScheduler
        )
    }

    @Test
    fun `should fail when scheduling campaign with invalid configuration`() {
        // given
        val campaignConfiguration = CampaignConfiguration(
            name = "ju",
            scenarios = mapOf("Scenario1" to ScenarioRequest(5))
        )
        val executeRequest = HttpRequest.POST("/schedule", campaignConfiguration)

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
            }.isEqualTo("""{"errors":[{"property":"configuration.name","message":"size must be between 3 and 300"}]}""")
        }
        confirmVerified(
            campaignService,
            campaignExecutor,
            campaignReportProvider,
            campaignReportStateKeeper,
            clusterFactoryService,
            campaignScheduler
        )
    }

    @Test
    fun `should successfully update a scheduled campaign given the right configurations`() {
        // given
        val campaignConfiguration = CampaignConfiguration(
            name = "This is a campaign",
            scenarios = mapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )
        val campaignKey = "my-campaign"
        val runningCampaign = relaxedMockk<RunningCampaign> {
            every { key } returns campaignKey
        }
        coEvery {
            campaignScheduler.update(
                Defaults.TENANT,
                Defaults.USERNAME,
                campaignKey,
                eq(campaignConfiguration)
            )
        } returns runningCampaign
        val updatedCampaign = Campaign(
            creation = Instant.now(),
            version = Instant.now(),
            key = RandomStringUtils.randomAlphanumeric(10),
            name = "This is a campaign",
            speedFactor = 1.0,
            start = Instant.now(),
            scheduledMinions = 123,
            end = null,
            configurerName = Defaults.USERNAME,
            aborterName = Defaults.USERNAME,
            status = IN_PROGRESS,
            scenarios = listOf(
                Scenario(version = Instant.now().minusSeconds(3), name = "scenario-1", minionsCount = 2534),
                Scenario(version = Instant.now().minusSeconds(21312), name = "scenario-2", minionsCount = 45645)
            )
        )
        coEvery { campaignService.retrieve(Defaults.TENANT, "my-campaign") } returns updatedCampaign

        // when
        val executeRequest = HttpRequest.PUT("/schedule/$campaignKey", campaignConfiguration)
        val response = httpClient.toBlocking().exchange(
            executeRequest,
            Campaign::class.java
        )

        // then
        coVerifyOrder {
            // Called with the default user.
            campaignScheduler.update(Defaults.TENANT, Defaults.USERNAME, campaignKey, eq(campaignConfiguration))
            campaignService.retrieve(Defaults.TENANT, "my-campaign")
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(updatedCampaign)
        }
        confirmVerified(
            campaignService,
            campaignExecutor,
            campaignReportProvider,
            campaignReportStateKeeper,
            clusterFactoryService,
            campaignScheduler
        )
    }

    @Test
    fun `should throw an exception to updating a scheduled campaign with unknown campaign Key`() {
        // given
        val campaignConfiguration = CampaignConfiguration(
            name = "This is a campaign",
            scenarios = mapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )
        val campaignKey = "unknown-campaign"
        coEvery {
            campaignScheduler.update(
                Defaults.TENANT,
                Defaults.USERNAME,
                campaignKey,
                eq(campaignConfiguration)
            )
        } throws IllegalArgumentException("Campaign does not exist")

        // when
        val executeRequest = HttpRequest.PUT("/schedule/$campaignKey", campaignConfiguration)
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                executeRequest,
                Campaign::class.java
            )
        }

        // then
        coVerifyOrder {
            campaignScheduler.update(Defaults.TENANT, Defaults.USERNAME, campaignKey, eq(campaignConfiguration))
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
            transform("body") { it.response.getBody(ErrorResponse::class.java).get() }.prop(ErrorResponse::errors)
                .containsOnly("Campaign does not exist")
        }
        confirmVerified(campaignService)
    }

    @Test
    fun `should return only campaigns with status not contained in the excluded status list`() {
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
            status = SUCCESSFUL,
            configurerName = Defaults.USERNAME,
            scenarios = listOf(
                Scenario(version = Instant.now().minusSeconds(3), name = "scenario-1", minionsCount = 2534),
                Scenario(version = Instant.now().minusSeconds(21312), name = "scenario-2", minionsCount = 45645)
            )
        )
        val campaign2 = Campaign(
            creation = Instant.now(),
            version = Instant.now(),
            key = "campaign-2",
            name = "The second campaign",
            speedFactor = 1.0,
            scheduledMinions = 123,
            start = Instant.now(),
            end = Instant.now().plusSeconds(1000),
            status = WARNING,
            configurerName = Defaults.USERNAME,
            scenarios = listOf(
                Scenario(version = Instant.now().minusSeconds(3), name = "scenario-3", minionsCount = 2534),
                Scenario(version = Instant.now().minusSeconds(21312), name = "scenario-4", minionsCount = 45645)
            )
        )
        val listsRequest = HttpRequest.GET<Page<Campaign>>("/?excludedStatuses=SUCCESSFUL")

        coEvery {
            campaignService.search(
                Defaults.TENANT,
                emptyList(),
                "start:desc",
                0,
                20,
                listOf(SUCCESSFUL)
            )
        } returns Page(0, 1, 1, listOf(campaign2))

        // when
        val response = httpClient.toBlocking().exchange(
            listsRequest, Argument.of(Page::class.java, Campaign::class.java)
        )

        //then
        coVerifyOnce { campaignService.search(Defaults.TENANT, emptyList(), "start:desc", 0, 20, listOf(SUCCESSFUL)) }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(Page(0, 1, 1, listOf(campaign2)))
        }
        confirmVerified(
            campaignService,
            campaignExecutor,
            campaignReportProvider,
            campaignReportStateKeeper,
            clusterFactoryService,
            campaignScheduler
        )
    }
}