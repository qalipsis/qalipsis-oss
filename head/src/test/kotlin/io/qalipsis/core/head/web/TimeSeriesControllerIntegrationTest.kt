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
import assertk.assertions.isEqualTo
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.excludeRecords
import io.mockk.impl.annotations.MockK
import io.qalipsis.api.query.Page
import io.qalipsis.api.report.TimeSeriesAggregationResult
import io.qalipsis.api.report.TimeSeriesEvent
import io.qalipsis.api.report.TimeSeriesMeter
import io.qalipsis.api.report.TimeSeriesRecord
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.report.AggregationQueryExecutionRequest
import io.qalipsis.core.head.report.CampaignSummaryResult
import io.qalipsis.core.head.report.DataRetrievalQueryExecutionRequest
import io.qalipsis.core.head.report.TimeSeriesDataQueryService
import io.qalipsis.core.head.report.WidgetService
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.SINGLE_HEAD])
@PropertySource(Property(name = "micronaut.server.log-handled-exceptions", value = "true"))
internal class TimeSeriesControllerIntegrationTest {

    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

    @MockK
    private lateinit var timeSeriesDataQueryService: TimeSeriesDataQueryService

    @MockK
    private lateinit var widgetService: WidgetService

    @MockBean(TimeSeriesDataQueryService::class)
    fun timeSeriesDataQueryService() = timeSeriesDataQueryService

    @MockBean(WidgetService::class)
    fun widgetService() = widgetService

    @BeforeEach
    internal fun setUp() {
        excludeRecords {
            timeSeriesDataQueryService.toString()
            timeSeriesDataQueryService.hashCode()
            widgetService.hashCode()
        }
    }

    @Test
    internal fun `should aggregate the time-series data with specified values`() {
        // given
        val start = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(3600)
        val end = start.plusSeconds(15)
        val aggregationResult = mapOf(
            "series-1" to listOf(
                TimeSeriesAggregationResult(start, Duration.ZERO, "my-campaign", 12.34.toBigDecimal()),
                TimeSeriesAggregationResult(
                    start.plusSeconds(5),
                    Duration.ofSeconds(5),
                    "my-campaign",
                    11.54.toBigDecimal()
                ),
                TimeSeriesAggregationResult(
                    start.plusSeconds(10),
                    Duration.ofSeconds(10),
                    "my-campaign",
                    13.68.toBigDecimal()
                ),
            ),
            "series-2" to listOf(
                TimeSeriesAggregationResult(start, Duration.ZERO, "my-campaign", 234.564565.toBigDecimal()),
                TimeSeriesAggregationResult(
                    start.plusSeconds(5),
                    Duration.ofSeconds(5),
                    "my-campaign",
                    1001.234242.toBigDecimal()
                ),
                TimeSeriesAggregationResult(
                    start.plusSeconds(10),
                    Duration.ofSeconds(10),
                    "my-campaign",
                    1234.432323.toBigDecimal()
                ),
            )
        )
        coEvery { timeSeriesDataQueryService.render(any(), any(), any()) } returns aggregationResult

        val request =
            HttpRequest.GET<Unit>(
                "/time-series/aggregate?series=ser-1,ser-2&campaigns=camp-1,camp2&scenarios=scen-1,scen-2&from=$start&until=$end&timeframe=PT10S"
            )

        // when
        val response: HttpResponse<Map<String, List<TimeSeriesAggregationResult>>> = httpClient.toBlocking().exchange(
            request,
            Argument.mapOf(Argument.of(String::class.java), Argument.listOf(TimeSeriesAggregationResult::class.java))
        )

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(aggregationResult)
        }

        coVerifyOnce {
            timeSeriesDataQueryService.render(
                tenant = Defaults.TENANT,
                dataSeriesReferences = setOf("ser-1", "ser-2"),
                queryExecutionRequest = AggregationQueryExecutionRequest(
                    campaignsReferences = setOf("camp-1", "camp2"),
                    scenariosNames = setOf("scen-1", "scen-2"),
                    from = start,
                    until = start.plusSeconds(15),
                    aggregationTimeframe = Duration.ofSeconds(10)
                )
            )
        }
        confirmVerified(timeSeriesDataQueryService)
    }

    @Test
    internal fun `should aggregate the time-series data with default values`() {
        // given
        val start = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(3600)
        val aggregationResult = mapOf(
            "series-1" to listOf(
                TimeSeriesAggregationResult(start, Duration.ZERO, "my-campaign", 12.34.toBigDecimal()),
                TimeSeriesAggregationResult(
                    start.plusSeconds(5),
                    Duration.ofSeconds(5),
                    "my-campaign",
                    11.54.toBigDecimal()
                ),
                TimeSeriesAggregationResult(
                    start.plusSeconds(10),
                    Duration.ofSeconds(10),
                    "my-campaign",
                    13.68.toBigDecimal()
                ),
            ),
            "series-2" to listOf(
                TimeSeriesAggregationResult(start, Duration.ZERO, "my-campaign", 234.564565.toBigDecimal()),
                TimeSeriesAggregationResult(
                    start.plusSeconds(5),
                    Duration.ofSeconds(5),
                    "my-campaign",
                    1001.234242.toBigDecimal()
                ),
                TimeSeriesAggregationResult(
                    start.plusSeconds(10),
                    Duration.ofSeconds(10),
                    "my-campaign",
                    1234.432323.toBigDecimal()
                ),
            )
        )
        coEvery { timeSeriesDataQueryService.render(any(), any(), any()) } returns aggregationResult

        val request =
            HttpRequest.GET<Unit>(
                "/time-series/aggregate?series=ser-1,ser-2&campaigns=camp-1,camp2"
            )

        // when
        val response: HttpResponse<Map<String, List<TimeSeriesAggregationResult>>> = httpClient.toBlocking().exchange(
            request,
            Argument.mapOf(Argument.of(String::class.java), Argument.listOf(TimeSeriesAggregationResult::class.java))
        )

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(aggregationResult)
        }

        coVerifyOnce {
            timeSeriesDataQueryService.render(
                tenant = Defaults.TENANT,
                dataSeriesReferences = setOf("ser-1", "ser-2"),
                queryExecutionRequest = AggregationQueryExecutionRequest(campaignsReferences = setOf("camp-1", "camp2"))
            )
        }
        confirmVerified(timeSeriesDataQueryService)
    }

    @Test
    internal fun `should not aggregate the time-series data when no campaign is set`() {
        // given
        val request = HttpRequest.GET<Unit>("/time-series/aggregate?series=ser-1,ser-2")

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                request,
                Argument.mapOf(
                    Argument.of(String::class.java),
                    Argument.listOf(TimeSeriesAggregationResult::class.java)
                )
            )
        }.response

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
            transform("body") {
                it.getBody(String::class.java).get()
            }.isEqualTo("""{"errors":["Required QueryValue [campaigns] not specified"]}""")
        }
        confirmVerified(timeSeriesDataQueryService)
    }

    @Test
    internal fun `should not aggregate the time-series data when no data-series is set`() {
        // given
        val request = HttpRequest.GET<Unit>("/time-series/aggregate?campaigns=camp-1,camp2")

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                request,
                Argument.mapOf(
                    Argument.of(String::class.java),
                    Argument.listOf(TimeSeriesAggregationResult::class.java)
                )
            )
        }.response

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
            transform("body") {
                it.getBody(String::class.java).get()
            }.isEqualTo("""{"errors":["Required QueryValue [series] not specified"]}""")
        }
        confirmVerified(timeSeriesDataQueryService)
    }

    @Test
    internal fun `should not aggregate the time-series data when from is after until`() {
        // given
        val start = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(3600)
        val end = start.minusSeconds(15)

        val request =
            HttpRequest.GET<Unit>("/time-series/aggregate?series=ser-1,ser-2&campaigns=camp-1,camp2&from=$start&until=$end")

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                request,
                Argument.mapOf(
                    Argument.of(String::class.java),
                    Argument.listOf(TimeSeriesAggregationResult::class.java)
                )
            )
        }.response

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
            transform("body") {
                it.getBody(String::class.java).get()
            }.contains("""{"errors":["The start instant of retrieval should be before the end, please check from and until arguments"]}""")
        }
        confirmVerified(timeSeriesDataQueryService)
    }

    @Test
    internal fun `should not aggregate the time-series data when the timeframe is 0`() {
        // given
        val request =
            HttpRequest.GET<Unit>("/time-series/aggregate?series=ser-1,ser-2&campaigns=camp-1,camp2&timeframe=PT0S")

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                request,
                Argument.mapOf(
                    Argument.of(String::class.java),
                    Argument.listOf(TimeSeriesAggregationResult::class.java)
                )
            )
        }.response

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
            transform("body") {
                it.getBody(String::class.java).get()
            }.contains("""{"errors":[{"property":"timeframe","message":"duration should be strictly positive but was PT0S"}]}""")
        }
        confirmVerified(timeSeriesDataQueryService)
    }

    @Test
    internal fun `should retrieve the time-series data with specified values`() {
        // given
        val start = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(3600)
        val end = start.plusSeconds(15)
        val searchResult = mapOf(
            "series-1" to Page(
                page = 4,
                totalPages = 3,
                totalElements = 1027,
                elements = listOf(
                    TimeSeriesEvent("my-event", "info", start, number = 12.34.toBigDecimal()),
                    TimeSeriesEvent("my-event", "info", start.plusMillis(1), number = 11.54.toBigDecimal()),
                    TimeSeriesEvent("my-event", "info", start.plusMillis(10), number = 13.68.toBigDecimal()),
                )
            ),
            "series-2" to Page(
                page = 4,
                totalPages = 3,
                totalElements = 1027,
                elements = listOf(
                    TimeSeriesMeter("my-meter", start, "gauge", value = 12.34.toBigDecimal()),
                    TimeSeriesMeter("my-meter", start.plusMillis(7), "gauge", value = 11.54.toBigDecimal()),
                    TimeSeriesMeter("my-meter", start.plusMillis(102), "gauge", value = 13.68.toBigDecimal()),
                )
            )
        )
        coEvery { timeSeriesDataQueryService.search(any(), any(), any()) } returns searchResult

        val request =
            HttpRequest.GET<Unit>(
                "/time-series/search?series=ser-1,ser-2&campaigns=camp-1,camp2&scenarios=scen-1,scen-2&from=$start&until=$end&timeframe=PT10S&page=3&size=256"
            )

        // when
        val response: HttpResponse<Map<String, Page<*>>> = httpClient.toBlocking().exchange(
            request,
            Argument.mapOf(Argument.of(String::class.java), Argument.of(Page::class.java, TimeSeriesRecord::class.java))
        )

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(searchResult)
        }

        coVerifyOnce {
            timeSeriesDataQueryService.search(
                tenant = Defaults.TENANT,
                dataSeriesReferences = setOf("ser-1", "ser-2"),
                queryExecutionRequest = DataRetrievalQueryExecutionRequest(
                    campaignsReferences = setOf("camp-1", "camp2"),
                    scenariosNames = setOf("scen-1", "scen-2"),
                    from = start,
                    until = start.plusSeconds(15),
                    aggregationTimeframe = Duration.ofSeconds(10),
                    page = 3,
                    size = 256
                )
            )
        }
        confirmVerified(timeSeriesDataQueryService)
    }

    @Test
    internal fun `should retrieve the time-series data with default values`() {
        // given
        val start = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(3600)
        val end = start.plusSeconds(15)
        val searchResult = mapOf(
            "series-1" to Page(
                page = 0,
                totalPages = 1,
                totalElements = 3,
                elements = listOf(
                    TimeSeriesEvent("my-event", "info", start, number = 12.34.toBigDecimal()),
                    TimeSeriesEvent("my-event", "info", start.plusMillis(1), number = 11.54.toBigDecimal()),
                    TimeSeriesEvent("my-event", "info", start.plusMillis(10), number = 13.68.toBigDecimal()),
                )
            ),
            "series-2" to Page(
                page = 0,
                totalPages = 1,
                totalElements = 3,
                elements = listOf(
                    TimeSeriesMeter("my-meter", start, "gauge", value = 12.34.toBigDecimal()),
                    TimeSeriesMeter("my-meter", start.plusMillis(7), "gauge", value = 11.54.toBigDecimal()),
                    TimeSeriesMeter("my-meter", start.plusMillis(102), "gauge", value = 13.68.toBigDecimal()),
                )
            )
        )
        coEvery { timeSeriesDataQueryService.search(any(), any(), any()) } returns searchResult

        val request =
            HttpRequest.GET<Unit>(
                "/time-series/search?series=ser-1,ser-2&campaigns=camp-1,camp2&from=$start&until=$end"
            )

        // when
        val response: HttpResponse<Map<String, Page<*>>> = httpClient.toBlocking().exchange(
            request,
            Argument.mapOf(Argument.of(String::class.java), Argument.of(Page::class.java, TimeSeriesRecord::class.java))
        )

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(searchResult)
        }

        coVerifyOnce {
            timeSeriesDataQueryService.search(
                tenant = Defaults.TENANT,
                dataSeriesReferences = setOf("ser-1", "ser-2"),
                queryExecutionRequest = DataRetrievalQueryExecutionRequest(
                    campaignsReferences = setOf("camp-1", "camp2"),
                    from = start,
                    until = start.plusSeconds(15),
                    aggregationTimeframe = null,
                    page = 0,
                    size = 500
                )
            )
        }
        confirmVerified(timeSeriesDataQueryService)
    }

    @Test
    internal fun `should not retrieve the time-series data when no campaign is set`() {
        // given
        val start = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(3600)
        val end = start.plusSeconds(15)

        val request = HttpRequest.GET<Unit>("/time-series/search?series=ser-1,ser-2&from=$start&until=$end")

        // when

        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                request,
                Argument.mapOf(
                    Argument.of(String::class.java),
                    Argument.of(Page::class.java, TimeSeriesRecord::class.java)
                )
            )
        }.response

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
            transform("body") {
                it.getBody(String::class.java).get()
            }.isEqualTo("""{"errors":["Required QueryValue [campaigns] not specified"]}""")
        }
        confirmVerified(timeSeriesDataQueryService)
    }

    @Test
    internal fun `should not retrieve the time-series data when no data-series is set`() {
        // given
        val start = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(3600)
        val end = start.plusSeconds(15)

        val request = HttpRequest.GET<Unit>("/time-series/search?campaigns=camp-1,camp2&from=$start&until=$end")

        // when

        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                request,
                Argument.mapOf(
                    Argument.of(String::class.java),
                    Argument.of(Page::class.java, TimeSeriesRecord::class.java)
                )
            )
        }.response

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
            transform("body") {
                it.getBody(String::class.java).get()
            }.isEqualTo("""{"errors":["Required QueryValue [series] not specified"]}""")
        }
        confirmVerified(timeSeriesDataQueryService)
    }

    @Test
    internal fun `should not retrieve the time-series data when from is after until`() {
        // given
        val start = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(3600)
        val end = start.minusSeconds(15)

        val request =
            HttpRequest.GET<Unit>("/time-series/search?series=ser-1,ser-2&campaigns=camp-1,camp2&from=$start&until=$end")

        // when

        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                request,
                Argument.mapOf(
                    Argument.of(String::class.java),
                    Argument.of(Page::class.java, TimeSeriesRecord::class.java)
                )
            )
        }.response

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
            transform("body") {
                it.getBody(String::class.java).get()
            }.isEqualTo("""{"errors":["The start instant of retrieval should be before the end, please check from and until arguments"]}""")
        }
        confirmVerified(timeSeriesDataQueryService)
    }

    @Test
    internal fun `should not retrieve the time-series data when the timeframe is 0`() {
        // given
        val start = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(3600)
        val end = start.plusSeconds(15)

        val request =
            HttpRequest.GET<Unit>("/time-series/search?series=ser-1,ser-2&campaigns=camp-1,camp2&from=$start&until=$end&timeframe=PT0S")

        // when

        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                request,
                Argument.mapOf(
                    Argument.of(String::class.java),
                    Argument.of(Page::class.java, TimeSeriesRecord::class.java)
                )
            )
        }.response

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
            transform("body") {
                it.getBody(String::class.java).get()
            }.isEqualTo("""{"errors":[{"property":"timeframe","message":"duration should be strictly positive but was PT0S"}]}""")
        }
        confirmVerified(timeSeriesDataQueryService)
    }

    @Test
    internal fun `should not retrieve the time-series data when the page is negative`() {
        // given
        val start = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(3600)
        val end = start.plusSeconds(15)

        val request =
            HttpRequest.GET<Unit>("/time-series/search?series=ser-1,ser-2&campaigns=camp-1,camp2&from=$start&until=$end&page=-1")

        // when

        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                request,
                Argument.mapOf(
                    Argument.of(String::class.java),
                    Argument.of(Page::class.java, TimeSeriesRecord::class.java)
                )
            )
        }.response

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
            transform("body") {
                it.getBody(String::class.java).get()
            }.isEqualTo("""{"errors":[{"property":"page","message":"must be greater than or equal to 0"}]}""")
        }
        confirmVerified(timeSeriesDataQueryService)
    }

    @Test
    internal fun `should not retrieve the time-series data when the size is 0`() {
        // given
        val start = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(3600)
        val end = start.plusSeconds(15)

        val request =
            HttpRequest.GET<Unit>("/time-series/search?series=ser-1,ser-2&campaigns=camp-1,camp2&from=$start&until=$end&size=0")

        // when

        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                request,
                Argument.mapOf(
                    Argument.of(String::class.java),
                    Argument.of(Page::class.java, TimeSeriesRecord::class.java)
                )
            )
        }.response

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
            transform("body") {
                it.getBody(String::class.java).get()
            }.isEqualTo("""{"errors":[{"property":"size","message":"must be greater than 0"}]}""")
        }
        confirmVerified(timeSeriesDataQueryService)
    }

    @Test
    internal fun `should not retrieve the time-series data when the size is too high`() {
        // given
        val start = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(3600)
        val end = start.plusSeconds(15)

        val request =
            HttpRequest.GET<Unit>("/time-series/search?series=ser-1,ser-2&campaigns=camp-1,camp2&from=$start&until=$end&size=10001")

        // when

        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                request,
                Argument.mapOf(
                    Argument.of(String::class.java),
                    Argument.of(Page::class.java, TimeSeriesRecord::class.java)
                )
            )
        }.response

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
            transform("body") {
                it.getBody(String::class.java).get()
            }.isEqualTo("""{"errors":[{"property":"size","message":"must be less than or equal to 10000"}]}""")
        }
        confirmVerified(timeSeriesDataQueryService)
    }

    @Test
    internal fun `should retrieve the campaign summary within the specified start, end and interval`() {
        // given
        val from = Instant.now().minus(7, ChronoUnit.HOURS)
        val until = Instant.now().minus(3, ChronoUnit.HOURS).minus(50, ChronoUnit.MINUTES)
        val timeOffset = 0.30F
        val timeframe = Duration.of(1, ChronoUnit.HOURS).minusMinutes(20)
        val calcStart = from.minus(30, ChronoUnit.MINUTES)
        val campaignSummaryList = listOf(
            CampaignSummaryResult(calcStart, 111, 101),
            CampaignSummaryResult(calcStart.minus(timeframe), 411, 543),
            CampaignSummaryResult(calcStart.minus(timeframe).minus(timeframe), 26, 2),
        )
        val request =
            HttpRequest.GET<List<CampaignSummaryResult>>("/time-series/summary/campaign-status?from=$from&until=$until&timeframe=$timeframe&timeOffset=$timeOffset")
        coEvery {
            widgetService.aggregateCampaignResult(any(), any(), any(), any(), any())
        } returns campaignSummaryList

        // when
        val response: HttpResponse<List<CampaignSummaryResult>> = httpClient.toBlocking().exchange(
            request,
            Argument.listOf(CampaignSummaryResult::class.java)
        )

        // then
        coVerifyOnce {
            widgetService.aggregateCampaignResult(
                tenant = Defaults.TENANT,
                from = from,
                until = until,
                timeOffset = timeOffset,
                aggregationTimeframe = timeframe
            )
        }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(campaignSummaryList)
        }
        confirmVerified(widgetService)
    }

    @Test
    internal fun `should retrieve the campaign summary when start, end and timeframe is not passed in`() {
        // given
        val timeOffset = 0.30F
        val startInterval = Instant.now().minus(30, ChronoUnit.MINUTES)
        val timeframe = Duration.of(1, ChronoUnit.HOURS).minusMinutes(20)
        val campaignSummaryList = listOf(
            CampaignSummaryResult(startInterval, 111, 101),
            CampaignSummaryResult(startInterval.minus(timeframe), 411, 543),
            CampaignSummaryResult(startInterval.minus(timeframe).minus(timeframe), 26, 2),
            CampaignSummaryResult(
                startInterval.minus(timeframe).minus(timeframe).minus(timeframe), 523, 43
            ),
            CampaignSummaryResult(
                startInterval.minus(timeframe).minus(timeframe).minus(timeframe)
                    .minus(timeframe), 89, 101
            ),
            CampaignSummaryResult(
                startInterval.minus(timeframe).minus(timeframe).minus(timeframe)
                    .minus(timeframe).minus(timeframe), 201, 89
            ),
            CampaignSummaryResult(
                startInterval.minus(timeframe).minus(timeframe).minus(timeframe)
                    .minus(timeframe).minus(timeframe).minus(timeframe), 415, 895
            )
        )
        val request =
            HttpRequest.GET<List<CampaignSummaryResult>>("/time-series/summary/campaign-status?timeOffset=$timeOffset")
        coEvery {
            widgetService.aggregateCampaignResult(any(), any(), any(), any(), any())
        } returns campaignSummaryList

        // when
        val response: HttpResponse<List<CampaignSummaryResult>> = httpClient.toBlocking().exchange(
            request,
            Argument.listOf(CampaignSummaryResult::class.java)
        )

        // then
        coVerifyOnce {
            widgetService.aggregateCampaignResult(
                tenant = Defaults.TENANT,
                from = null,
                until = null,
                timeOffset = timeOffset,
                aggregationTimeframe = null
            )
        }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(campaignSummaryList)
        }
        confirmVerified(widgetService)
    }

    @Test
    fun `should throw an exception for an unauthorised tenant`() {
        // given
        val request =
            HttpRequest.GET<List<CampaignSummaryResult>>("/time-series/summary/campaign-status?timeOffset=1")
        request.header("X-Tenant", "unknown-tenant-reference")

        // when
        assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(request, CampaignSummaryResult::class.java)
        }

        // then
        coVerifyNever { widgetService.getFactoryStates("unknown-tenant-reference") }
    }

    @Test
    fun `should throw an exception when from is greater than until dates`() {
        // given
        val until = Instant.now().minus(7, ChronoUnit.HOURS)
        val from = Instant.now().minus(3, ChronoUnit.HOURS).minus(50, ChronoUnit.MINUTES)
        val timeOffset = 0.30F
        val timeframe = Duration.of(1, ChronoUnit.HOURS).minusMinutes(20)
        val request =
            HttpRequest.GET<List<CampaignSummaryResult>>("/time-series/summary/campaign-status?from=$from&until=$until&timeframe=$timeframe&timeOffset=$timeOffset")

        // when
        val exception = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(request, CampaignSummaryResult::class.java)
        }.response

        // then
        assertThat(exception).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
            transform("body") {
                it.getBody(String::class.java).get()
            }.contains("""{"errors":["The start instant of retrieval should be before the end, please check from and until arguments"]}""")
        }
        coVerifyNever { widgetService.getFactoryStates(Defaults.TENANT) }
    }

}


