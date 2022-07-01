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
import io.mockk.coJustRun
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.excludeRecords
import io.mockk.impl.annotations.MockK
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.AggregationOperation
import io.qalipsis.core.head.jdbc.entity.DataType
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.jdbc.entity.Operator
import io.qalipsis.core.head.model.CampaignReport
import io.qalipsis.core.head.model.ColorDataSeriesPatch
import io.qalipsis.core.head.model.DataSeries
import io.qalipsis.core.head.model.DataSeriesFilter
import io.qalipsis.core.head.model.DataSeriesPatch
import io.qalipsis.core.head.report.DataSeriesService
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.VOLATILE, ExecutionEnvironments.SINGLE_HEAD])
@PropertySource(Property(name = "micronaut.server.log-handled-exceptions", value = "true"))
internal class DataSeriesControllerIntegrationTest {

    @Inject
    @field:Client("/data-series")
    lateinit var httpClient: HttpClient

    @MockK
    private lateinit var dataSeriesService: DataSeriesService

    @MockBean(DataSeriesService::class)
    fun dataSeriesService() = dataSeriesService

    @BeforeEach
    internal fun setUp() {
        excludeRecords { dataSeriesService.hashCode() }
    }

    @Test
    fun `should successfully create data series`() {
        // given
        val dataSeries = DataSeries(
            displayName = "Time to response for complex query",
            dataType = DataType.EVENTS,
            color = "#ff761c",
            filters = setOf(DataSeriesFilter("step", Operator.IS, "http-post-complex-query")),
            fieldName = "duration",
            aggregationOperation = AggregationOperation.AVERAGE,
            timeframeUnit = Duration.ofSeconds(1),
            displayFormat = "#0.000"
        )
        val createdDataSeries = dataSeries.copy(
            color = "#FF761C", reference = "qoi78qwedqwiz"
        )
        coEvery { dataSeriesService.create(any(), any(), any()) } returns createdDataSeries

        // when
        val response = httpClient.toBlocking().exchange(HttpRequest.POST("/", dataSeries), DataSeries::class.java)

        // then
        coVerifyOrder {
            dataSeriesService.create(creator = Defaults.USER, tenant = Defaults.TENANT, dataSeries = dataSeries)
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(createdDataSeries)
        }
        confirmVerified(dataSeriesService)
    }

    @Test
    fun `should fail when creating an invalid data series`() {
        // given
        val dataSeries = DataSeries(displayName = "", dataType = DataType.EVENTS)
        val createDataSeriesRequest = HttpRequest.POST("/", dataSeries)

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(createDataSeriesRequest, DataSeries::class.java)
        }

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
            transform("body") {
                it.response.getBody(String::class.java).get()
            }.contains(""""message":"dataSeries.displayName: must not be blank"""")
        }
        confirmVerified(dataSeriesService)
    }

    @Test
    fun `should successfully update data series`() {
        // given
        val updatedDataSeries = DataSeries(
            displayName = "the-name",
            dataType = DataType.EVENTS,
            color = "the-color",
            filters = setOf(
                DataSeriesFilter("name", Operator.IS, "value")
            )
        )
        val dataSeriesPatch = ColorDataSeriesPatch("the-color")
        val updateDataSeriesRequest = HttpRequest.PATCH<List<DataSeriesPatch>>("/q7232x", listOf(dataSeriesPatch))
        coEvery {
            dataSeriesService.update(
                username = Defaults.USER,
                tenant = Defaults.TENANT,
                reference = "q7232x",
                patches = any()
            )
        } returns updatedDataSeries

        // when
        val response = httpClient.toBlocking().exchange(updateDataSeriesRequest, DataSeries::class.java)

        // then
        coVerifyOnce {
            dataSeriesService.update(
                username = Defaults.USER,
                tenant = Defaults.TENANT,
                reference = "q7232x",
                patches = any()
            )
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(updatedDataSeries)
        }
        confirmVerified(dataSeriesService)
    }

    @Test
    fun `should successfully delete the data series`() {
        // given
        coJustRun { dataSeriesService.delete(any(), any(), any()) }

        // when
        val response = httpClient.toBlocking().exchange(HttpRequest.DELETE("/q7232x", null), Unit::class.java)

        // then
        coVerifyOnce {
            dataSeriesService.delete(username = Defaults.USER, tenant = Defaults.TENANT, reference = "q7232x")
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.ACCEPTED)
        }
        confirmVerified(dataSeriesService)
    }

    @Test
    fun `should successfully get the data series`() {
        // given
        val dataSeries = DataSeries(
            displayName = "the-name",
            dataType = DataType.EVENTS,
            filters = setOf(
                DataSeriesFilter("name", Operator.IS, "value")
            )
        )
        val getDataSeriesRequest = HttpRequest.GET<CampaignReport>("/q7232x")
        coEvery {
            dataSeriesService.get(
                username = Defaults.USER,
                tenant = Defaults.TENANT,
                reference = "q7232x"
            )
        } returns dataSeries

        // when
        val response = httpClient.toBlocking().exchange(getDataSeriesRequest, DataSeries::class.java)

        // then
        coVerifyOnce {
            dataSeriesService.get(username = Defaults.USER, tenant = Defaults.TENANT, reference = "q7232x")
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(dataSeries)

        }
        confirmVerified(dataSeriesService)
    }
}