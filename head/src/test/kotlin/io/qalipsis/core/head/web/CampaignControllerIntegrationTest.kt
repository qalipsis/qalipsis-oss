package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
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
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignManager
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.jdbc.entity.ScenarioEntity
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignReport
import io.qalipsis.core.head.model.CampaignRequest
import io.qalipsis.core.head.model.Page
import io.qalipsis.core.head.model.Scenario
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.core.head.model.converter.CampaignConverter
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import jakarta.inject.Inject
import org.apache.commons.lang3.RandomStringUtils
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
    private lateinit var campaignConverter: CampaignConverter

    @MockBean(FactoryService::class)
    fun clusterFactoryService() = clusterFactoryService

    @MockBean(CampaignService::class)
    fun campaignService() = campaignService

    @MockBean(CampaignReportStateKeeper::class)
    fun campaignReportStateKeeper() = campaignReportStateKeeper

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
            configurerName = Defaults.USER,
            result = null,
            scenarios = listOf(
                Scenario(version = Instant.now().minusSeconds(3), name = "scenario-1", minionsCount = 2534),
                Scenario(version = Instant.now().minusSeconds(21312), name = "scenario-2", minionsCount = 45645)
            )
        )
        coEvery {
            campaignManager.start(
                Defaults.USER,
                "This is a campaign",
                refEq(campaignConfiguration)
            )
        } returns createdCampaign

        // when
        val executeRequest = HttpRequest.POST("/", campaignRequest)
        val response = httpClient.toBlocking().exchange(
            executeRequest,
            Campaign::class.java
        )

        // then
        coVerifyOrder {
            campaignConverter.convertRequest(Defaults.TENANT, campaignRequest)
            // Called with the default user.
            campaignManager.start(Defaults.USER, "This is a campaign", refEq(campaignConfiguration))
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
            }.isEqualTo("""{"errors":[{"property":"campaign.name","message":"size must be between 3 and 300"}]}""")
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

        coEvery { clusterFactoryService.getActiveScenarios(Defaults.TENANT, any()) } returns listOf(
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
        coEvery { clusterFactoryService.getActiveScenarios(Defaults.TENANT, any()) } returns emptyList()
        val validateRequest = HttpRequest.POST("/validate", campaignRequest)
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
    }

    @Test
    fun `should return page of campaigns`() {
        // given
        val campaign = Campaign(
            version = Instant.now(),
            key = "campaign-1",
            name = "The campaign",
            speedFactor = 1.0,
            start = Instant.now(),
            end = Instant.now().plusSeconds(1000),
            result = ExecutionStatus.SUCCESSFUL,
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
                null,
                0,
                20
            )
        } returns Page(0, 1, 1, listOf(campaign))

        // when
        val response = httpClient.toBlocking().exchange(
            listsRequest, Argument.of(Page::class.java, Campaign::class.java)
        )

        //then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(Page(0, 1, 1, listOf(campaign)))
        }
    }

    @Test
    fun `should return page of campaigns with filter`() {
        // given
        val campaign = Campaign(
            version = Instant.now(),
            key = "campaign-1",
            name = "The campaign",
            speedFactor = 1.0,
            start = Instant.now(),
            end = Instant.now().plusSeconds(1000),
            result = ExecutionStatus.SUCCESSFUL,
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
                null,
                0,
                20
            )
        } returns Page(0, 1, 1, listOf(campaign))

        // when
        val response = httpClient.toBlocking().exchange(
            listsRequest, Argument.of(Page::class.java, Campaign::class.java)
        )

        //then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(Page(0, 1, 1, listOf(campaign)))
        }
    }

    @Test
    fun `should return page of campaigns with filter and sort`() {
        // given
        val campaign = Campaign(
            version = Instant.now(),
            key = "campaign-1",
            name = "The campaign",
            speedFactor = 1.0,
            start = Instant.now(),
            end = Instant.now().plusSeconds(1000),
            result = ExecutionStatus.SUCCESSFUL,
            configurerName = Defaults.USER,
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
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(Page(0, 1, 1, listOf(campaign)))
        }
    }

    @Test
    fun `should successfully abort the campaign`() {
        // given
        val abortRequest = HttpRequest.POST("/first_campaign/abort", null)

        // when
        val response = httpClient.toBlocking().exchange(abortRequest, Unit::class.java)

        // then
        coVerifyOnce {
            campaignManager.abort(Defaults.USER, Defaults.TENANT, "first_campaign", false)
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.ACCEPTED)
        }
    }

    @Test
    fun `should successfully abort the campaign hard`() {
        // given
        val abortRequest = HttpRequest.POST("/first_campaign/abort?hard=true", null)

        // when
        val response = httpClient.toBlocking().exchange(abortRequest, Unit::class.java)

        // then
        coVerifyOnce {
            campaignManager.abort(Defaults.USER, Defaults.TENANT, "first_campaign", true)
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.ACCEPTED)
        }
    }

    @Test
    fun `should successfully get the campaign report`() {
        // given
        val campaignReport = relaxedMockk<io.qalipsis.api.report.CampaignReport>()
        val convertedCampaignReport =
            CampaignReport(
                campaignKey = "my-campaign",
                start = Instant.now().minusMillis(1111),
                end = Instant.now(),
                startedMinions = 0,
                completedMinions = 0,
                successfulExecutions = 0,
                failedExecutions = 0,
                status = ExecutionStatus.SUCCESSFUL,
                scenariosReports = listOf(
                    io.qalipsis.core.head.model.ScenarioReport(
                        campaignKey = "my-campaign",
                        scenarioName = "my-scenario-1",
                        start = Instant.now().minusMillis(1111),
                        end = Instant.now(),
                        startedMinions = 0,
                        completedMinions = 0,
                        successfulExecutions = 0,
                        failedExecutions = 0,
                        status = ExecutionStatus.FAILED,
                        messages = listOf(
                            io.qalipsis.core.head.model.ReportMessage(
                                stepName = "my-step-1",
                                messageId = "message-id-1",
                                severity = ReportMessageSeverity.INFO,
                                message = "Hello from test 1"
                            )
                        )
                    ),
                    io.qalipsis.core.head.model.ScenarioReport(
                        campaignKey = "my-campaign",
                        scenarioName = "my-scenario-2",
                        start = Instant.now().minusMillis(1111),
                        end = Instant.now(),
                        startedMinions = 1,
                        completedMinions = 1,
                        successfulExecutions = 1,
                        failedExecutions = 1,
                        status = ExecutionStatus.ABORTED,
                        messages = listOf(
                            io.qalipsis.core.head.model.ReportMessage(
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
        coEvery { campaignReportStateKeeper.generateReport("first_campaign") } returns campaignReport
        coEvery { campaignConverter.convertReport(campaignReport) } returns convertedCampaignReport

        // when
        val response = httpClient.toBlocking().exchange(getCampaignReportRequest, CampaignReport::class.java)

        // then
        coVerifyOnce {
            campaignReportStateKeeper.generateReport("first_campaign")
            campaignConverter.convertReport(campaignReport)
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(convertedCampaignReport)

        }
    }
}