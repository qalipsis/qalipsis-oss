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
import io.qalipsis.core.head.report.DataRetrievalQueryExecutionRequest
import io.qalipsis.core.head.report.TimeSeriesDataQueryService
import io.qalipsis.test.mockk.WithMockk
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

    @MockBean(TimeSeriesDataQueryService::class)
    fun timeSeriesDataQueryService() = timeSeriesDataQueryService


    @BeforeEach
    internal fun setUp() {
        excludeRecords {
            timeSeriesDataQueryService.toString()
            timeSeriesDataQueryService.hashCode()
        }
    }

    @Test
    internal fun `should aggregate the time-series data with specified values`() {
        // given
        val start = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(3600)
        val end = start.plusSeconds(15)
        val aggregationResult = mapOf(
            "series-1" to listOf(
                TimeSeriesAggregationResult(start, Duration.ZERO, 12.34.toBigDecimal()),
                TimeSeriesAggregationResult(start.plusSeconds(5), Duration.ofSeconds(5), 11.54.toBigDecimal()),
                TimeSeriesAggregationResult(start.plusSeconds(10), Duration.ofSeconds(10), 13.68.toBigDecimal()),
            ),
            "series-2" to listOf(
                TimeSeriesAggregationResult(start, Duration.ZERO, 234.564565.toBigDecimal()),
                TimeSeriesAggregationResult(start.plusSeconds(5), Duration.ofSeconds(5), 1001.234242.toBigDecimal()),
                TimeSeriesAggregationResult(start.plusSeconds(10), Duration.ofSeconds(10), 1234.432323.toBigDecimal()),
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
                TimeSeriesAggregationResult(start, Duration.ZERO, 12.34.toBigDecimal()),
                TimeSeriesAggregationResult(start.plusSeconds(5), Duration.ofSeconds(5), 11.54.toBigDecimal()),
                TimeSeriesAggregationResult(start.plusSeconds(10), Duration.ofSeconds(10), 13.68.toBigDecimal()),
            ),
            "series-2" to listOf(
                TimeSeriesAggregationResult(start, Duration.ZERO, 234.564565.toBigDecimal()),
                TimeSeriesAggregationResult(start.plusSeconds(5), Duration.ofSeconds(5), 1001.234242.toBigDecimal()),
                TimeSeriesAggregationResult(start.plusSeconds(10), Duration.ofSeconds(10), 1234.432323.toBigDecimal()),
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
}