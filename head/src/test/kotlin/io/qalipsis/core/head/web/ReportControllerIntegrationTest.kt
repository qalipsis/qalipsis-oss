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
import io.mockk.coJustRun
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.excludeRecords
import io.mockk.impl.annotations.MockK
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.model.Page
import io.qalipsis.core.head.model.Report
import io.qalipsis.core.head.model.ReportCreationAndUpdateRequest
import io.qalipsis.core.head.report.ReportService
import io.qalipsis.core.head.report.SharingMode
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/**
 * @author Joël Valère
 */

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.SINGLE_HEAD])
@PropertySource(Property(name = "micronaut.server.log-handled-exceptions", value = "true"))
internal class ReportControllerIntegrationTest {

    @Inject
    @field:Client("/reports")
    lateinit var httpClient: HttpClient

    @MockK
    private lateinit var reportService: ReportService

    @MockBean(ReportService::class)
    fun reportSeriesService() = reportService

    @BeforeEach
    internal fun setUp() {
        excludeRecords { reportService.hashCode() }
    }

    @Test
    fun `should successfully create report with only display name`() {
        // given
        val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
            displayName = "report defined only with name",
        )
        val createdReport = Report(
            reference = "qoi78wizened",
            version = Instant.EPOCH,
            creator = "my name",
            displayName = "report defined only with name",
            campaignKeys = emptyList(),
            campaignNamesPatterns = emptyList(),
            resolvedCampaignKeys = emptyList(),
            scenarioNamesPatterns = emptyList(),
            resolvedScenarioNames = emptyList(),
            dataComponents = emptyList()
        )
        coEvery { reportService.create(any(), any(), any()) } returns createdReport

        // when
        val response = httpClient.toBlocking().exchange(HttpRequest.POST("/", reportCreationAndUpdateRequest), Report::class.java)

        // then
        coVerifyOrder {
            reportService.create(
                tenant = Defaults.TENANT,
                creator = Defaults.USER,
                reportCreationAndUpdateRequest = reportCreationAndUpdateRequest
            )
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(createdReport)
        }
        confirmVerified(reportService)
    }

    @Test
    fun `should successfully create report with all fields`() {
        // given
        val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
            displayName = "report-name",
            sharingMode = SharingMode.NONE,
            campaignKeys = emptyList(),
            campaignNamesPatterns = emptyList(),
            scenarioNamesPatterns = emptyList(),
            dataComponents = emptyList()
        )
        val createdReport = Report(
            reference = "qoi78wizened",
            version = Instant.EPOCH,
            sharingMode = SharingMode.NONE,
            creator = "my name",
            displayName = "report-name",
            campaignKeys = emptyList(),
            campaignNamesPatterns = emptyList(),
            resolvedCampaignKeys = emptyList(),
            scenarioNamesPatterns = emptyList(),
            resolvedScenarioNames = emptyList(),
            dataComponents = emptyList()
        )
        coEvery { reportService.create(any(), any(), any()) } returns createdReport

        // when
        val response = httpClient.toBlocking().exchange(HttpRequest.POST("/", reportCreationAndUpdateRequest), Report::class.java)

        // then
        coVerifyOrder {
            reportService.create(
                tenant = Defaults.TENANT,
                creator = Defaults.USER,
                reportCreationAndUpdateRequest = reportCreationAndUpdateRequest
            )
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(createdReport)
        }
        confirmVerified(reportService)
    }
    
    @Test
    fun `should fail when creating an invalid report due to invalid display name`() {
        // given
        val report = ReportCreationAndUpdateRequest(displayName = "")
        val reportCreationRequest = HttpRequest.POST("/", report)

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(reportCreationRequest, Report::class.java)
        }

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
            transform("body") {
                it.response.getBody(String::class.java).get()
            }.contains(""""message":"reportCreationAndUpdateRequest.displayName: must not be blank"""")
            transform("body") {
                it.response.getBody(String::class.java).get()
            }.contains(""""message":"reportCreationAndUpdateRequest.displayName: size must be between 1 and 200"""")
        }
        confirmVerified(reportService)
    }
    
    @Test
    fun `should successfully delete the report`() {
        // given
        coJustRun { reportService.delete(any(), any(), any()) }

        // when
        val response = httpClient.toBlocking().exchange(HttpRequest.DELETE("/q7232x", null), Unit::class.java)

        // then
        coVerifyOnce {
            reportService.delete(tenant = Defaults.TENANT, username = Defaults.USER, reference = "q7232x")
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.ACCEPTED)
        }
        confirmVerified(reportService)
    }

    @Test
    fun `should successfully get the report`() {
        // given
        val report = Report(
            reference = "q7232x",
            version = Instant.EPOCH,
            creator = "my name",
            displayName = "report defined only with name",
            campaignKeys = emptyList(),
            campaignNamesPatterns = emptyList(),
            resolvedCampaignKeys = emptyList(),
            scenarioNamesPatterns = emptyList(),
            resolvedScenarioNames = emptyList(),
            dataComponents = emptyList()
        )
        val getReportRequest = HttpRequest.GET<Report>("/q7232x")
        coEvery {
            reportService.get(
                tenant = Defaults.TENANT,
                username = Defaults.USER,
                reference = "q7232x"
            )
        } returns report

        // when
        val response = httpClient.toBlocking().exchange(getReportRequest, Report::class.java)

        // then
        coVerifyOnce {
            reportService.get(tenant = Defaults.TENANT, username = Defaults.USER, reference = "q7232x")
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(report)
        }
        confirmVerified(reportService)
    }

    @Test
    fun `should successfully update the report`() {
        // given
        val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
            displayName = "updated-report",
        )
        val updatedReport = Report(
            reference = "q721wx52",
            version = Instant.EPOCH,
            creator = "my name",
            displayName = "updated-report",
            campaignKeys = emptyList(),
            campaignNamesPatterns = emptyList(),
            resolvedCampaignKeys = emptyList(),
            scenarioNamesPatterns = emptyList(),
            resolvedScenarioNames = emptyList(),
            dataComponents = emptyList()
        )
        coEvery { reportService.update(any(), any(), "q721wx52", any()) } returns updatedReport

        // when
        val response = httpClient.toBlocking().exchange(HttpRequest.PUT("/q721wx52", reportCreationAndUpdateRequest), Report::class.java)

        // then
        coVerifyOrder {
            reportService.update(
                tenant = Defaults.TENANT,
                username = Defaults.USER,
                reference = "q721wx52",
                reportCreationAndUpdateRequest = reportCreationAndUpdateRequest
            )
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(updatedReport)
        }
        confirmVerified(reportService)
    }

    @Test
    internal fun `should search reports with the default parameters`() {
        // given
        val report = Report(
            reference = "q721wx52",
            version = Instant.EPOCH,
            creator = "my name",
            displayName = "the-report",
            campaignKeys = emptyList(),
            campaignNamesPatterns = emptyList(),
            resolvedCampaignKeys = emptyList(),
            scenarioNamesPatterns = emptyList(),
            resolvedScenarioNames = emptyList(),
            dataComponents = emptyList()
        )
        val request = HttpRequest.GET<Page<Report>>("/")
        coEvery {
            reportService.search(
                tenant = Defaults.TENANT,
                username = Defaults.USER,
                filters = emptyList(),
                sort = null,
                page = 0,
                size = 20
            )
        } returns Page(0, 1, 1, listOf(report))

        // when
        val response = httpClient.toBlocking().exchange(request, Argument.of(Page::class.java, Report::class.java))

        //then
        coVerifyOnce {
            reportService.search(
                tenant = Defaults.TENANT,
                username = Defaults.USER,
                filters = emptyList(),
                sort = null,
                page = 0,
                size = 20
            )
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(Page(0, 1, 1, listOf(report)))
        }
    }

    @Test
    internal fun `should fail for negative page value`() {
        // given
        val request = HttpRequest.GET<Page<Report>>("/?page=-2")

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(request, Argument.of(Page::class.java, Report::class.java))
        }.response

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @Test
    internal fun `should fail for negative size value`() {
        // given
        val request = HttpRequest.GET<Page<Report>>("/?size=-200")

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(request, Argument.of(Page::class.java, Report::class.java))
        }.response

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @Test
    internal fun `should search for reports with filter and default sorting`() {
        // given
        val report = Report(
            reference = "q721wx52-1",
            version = Instant.EPOCH,
            creator = "my name",
            displayName = "report-name-1",
            campaignKeys = emptyList(),
            campaignNamesPatterns = emptyList(),
            resolvedCampaignKeys = emptyList(),
            scenarioNamesPatterns = emptyList(),
            resolvedScenarioNames = emptyList(),
            dataComponents = emptyList()
        )
        val report2 = Report(
            reference = "q721wx52-2",
            version = Instant.EPOCH,
            creator = "my name",
            displayName = "report-name-2",
            campaignKeys = emptyList(),
            campaignNamesPatterns = emptyList(),
            resolvedCampaignKeys = emptyList(),
            scenarioNamesPatterns = emptyList(),
            resolvedScenarioNames = emptyList(),
            dataComponents = emptyList()
        )
        val request = HttpRequest.GET<Page<Report>>("?filter=foo,bar&size=4")
        coEvery {
            reportService.search(
                tenant = Defaults.TENANT,
                username = Defaults.USER,
                filters = listOf("foo", "bar"),
                sort = null,
                page = 0,
                size = 4
            )
        } returns Page(0, 1, 2, listOf(report, report2))

        // when
        val response = httpClient.toBlocking().exchange(request, Argument.of(Page::class.java, Report::class.java))

        // then
        coVerifyOnce {
            reportService.search(
                tenant = Defaults.TENANT,
                username = Defaults.USER,
                filters = listOf("foo", "bar"),
                sort = null,
                page = 0,
                size = 4
            )
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(Page(0, 1, 2, listOf(report, report2)))
        }
    }

    @Test
    internal fun `should search for reports with specified sorting`() {
        // given
        val report = Report(
            reference = "q721wx52-1",
            version = Instant.EPOCH,
            creator = "my name",
            displayName = "report-name-1",
            campaignKeys = emptyList(),
            campaignNamesPatterns = emptyList(),
            resolvedCampaignKeys = emptyList(),
            scenarioNamesPatterns = emptyList(),
            resolvedScenarioNames = emptyList(),
            dataComponents = emptyList()
        )
        val report2 = Report(
            reference = "q721wx52-2",
            version = Instant.EPOCH,
            creator = "my name",
            displayName = "report-name-2",
            campaignKeys = emptyList(),
            campaignNamesPatterns = emptyList(),
            resolvedCampaignKeys = emptyList(),
            scenarioNamesPatterns = emptyList(),
            resolvedScenarioNames = emptyList(),
            dataComponents = emptyList()
        )
        val request = HttpRequest.GET<Page<Report>>("?sort=displayName&size=5")
        coEvery {
            reportService.search(
                tenant = Defaults.TENANT,
                username = Defaults.USER,
                filters = emptyList(),
                sort = "displayName",
                page = 0,
                size = 5
            )
        } returns Page(0, 1, 2, listOf(report, report2))

        // when
        val response = httpClient.toBlocking().exchange(request, Argument.of(Page::class.java, Report::class.java))

        // then
        coVerifyOnce {
            reportService.search(
                tenant = Defaults.TENANT,
                username = Defaults.USER,
                filters = emptyList(),
                sort = "displayName",
                page = 0,
                size = 5
            )
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(Page(0, 1, 2, listOf(report, report2)))
        }
    }
}