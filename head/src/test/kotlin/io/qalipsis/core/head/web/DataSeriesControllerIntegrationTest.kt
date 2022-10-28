/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can #ff0000istribute it and/or modify
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
import io.qalipsis.api.query.Page
import io.qalipsis.api.query.QueryAggregationOperator.AVERAGE
import io.qalipsis.api.query.QueryClauseOperator
import io.qalipsis.api.query.QueryClauseOperator.IS
import io.qalipsis.api.report.DataField
import io.qalipsis.api.report.DataFieldType
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.model.ColorDataSeriesPatch
import io.qalipsis.core.head.model.DataSeries
import io.qalipsis.core.head.model.DataSeriesCreationRequest
import io.qalipsis.core.head.model.DataSeriesFilter
import io.qalipsis.core.head.model.DataSeriesPatch
import io.qalipsis.core.head.report.DataProvider
import io.qalipsis.core.head.report.DataSeriesService
import io.qalipsis.core.head.report.DataType
import io.qalipsis.core.head.report.DataType.EVENTS
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.TRANSIENT, ExecutionEnvironments.SINGLE_HEAD])
@PropertySource(Property(name = "micronaut.server.log-handled-exceptions", value = "true"))
internal class DataSeriesControllerIntegrationTest {

    @Inject
    @field:Client("/data-series")
    lateinit var httpClient: HttpClient

    @MockK
    private lateinit var dataSeriesService: DataSeriesService

    @MockK
    private lateinit var dataProvider: DataProvider

    @MockBean(DataSeriesService::class)
    fun dataSeriesService() = dataSeriesService

    @MockBean(DataProvider::class)
    fun dataProvider() = dataProvider

    @BeforeEach
    internal fun setUp() {
        excludeRecords { dataSeriesService.hashCode() }
        excludeRecords { dataProvider.hashCode() }
    }

    @Test
    fun `should successfully create data series`() {
        // given
        val dataSeries = DataSeriesCreationRequest(
            displayName = "Time to response for complex query",
            dataType = EVENTS,
            valueName = "my-event",
            color = "#ff761c",
            filters = setOf(DataSeriesFilter("step", IS, "http-post-complex-query")),
            fieldName = "duration",
            aggregationOperation = AVERAGE,
            timeframeUnit = Duration.ofSeconds(1),
            displayFormat = "#0.000"
        )
        val createdDataSeries = DataSeries(
            displayName = "Time to response for complex query",
            dataType = EVENTS,
            valueName = "my-other-event",
            color = "#ff761c",
            filters = setOf(DataSeriesFilter("step", IS, "http-post-complex-query")),
            fieldName = "duration",
            aggregationOperation = AVERAGE,
            timeframeUnit = Duration.ofSeconds(1),
            displayFormat = "#0.000"
        ).copy(
            color = "#FF761C", reference = "qoi78qwedqwiz"
        )
        coEvery { dataSeriesService.create(any(), any(), any()) } returns createdDataSeries

        // when
        val response = httpClient.toBlocking().exchange(HttpRequest.POST("/", dataSeries), DataSeries::class.java)

        // then
        coVerifyOrder {
            dataSeriesService.create(tenant = Defaults.TENANT, creator = Defaults.USER, dataSeries = dataSeries)
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
        val dataSeries = DataSeries(displayName = "", dataType = DataType.EVENTS, valueName = "my-event")
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
            }.all {
                contains("""{"property":"dataSeries.displayName","message":"must not be blank"}""")
                contains("""{"property":"dataSeries.displayName","message":"size must be between 3 and 200"}""")
            }
        }
        confirmVerified(dataSeriesService)
    }

    @Test
    fun `should successfully update data series`() {
        // given
        val updatedDataSeries = DataSeries(
            displayName = "the-name",
            dataType = DataType.EVENTS,
            valueName = "my-event",
            color = "the-color",
            filters = setOf(
                DataSeriesFilter("name", QueryClauseOperator.IS, "value")
            )
        )
        val dataSeriesPatch = ColorDataSeriesPatch("the-color")
        val updateDataSeriesRequest = HttpRequest.PATCH<List<DataSeriesPatch>>("/q7232x", listOf(dataSeriesPatch))
        coEvery {
            dataSeriesService.update(
                tenant = Defaults.TENANT,
                username = Defaults.USER,
                reference = "q7232x",
                patches = any()
            )
        } returns updatedDataSeries

        // when
        val response = httpClient.toBlocking().exchange(updateDataSeriesRequest, DataSeries::class.java)

        // then
        coVerifyOnce {
            dataSeriesService.update(
                tenant = Defaults.TENANT,
                username = Defaults.USER,
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
            dataSeriesService.delete(tenant = Defaults.TENANT, username = Defaults.USER, reference = "q7232x")
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
            valueName = "my-event",
            filters = setOf(
                DataSeriesFilter("name", QueryClauseOperator.IS, "value")
            )
        )
        val getDataSeriesRequest = HttpRequest.GET<DataSeries>("/q7232x")
        coEvery {
            dataSeriesService.get(
                tenant = Defaults.TENANT,
                username = Defaults.USER,
                reference = "q7232x"
            )
        } returns dataSeries

        // when
        val response = httpClient.toBlocking().exchange(getDataSeriesRequest, DataSeries::class.java)

        // then
        coVerifyOnce {
            dataSeriesService.get(tenant = Defaults.TENANT, username = Defaults.USER, reference = "q7232x")
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(dataSeries)

        }
        confirmVerified(dataSeriesService)
    }

    @Test
    internal fun `should search names for the events without filter`() {
        // given
        val names = listOf("event-1", "event-2")
        val request = HttpRequest.GET<List<String>>("/events/names")
        coEvery { dataProvider.searchNames(any(), any(), any(), any()) } returns names

        // when
        val response = httpClient.toBlocking().exchange(request, Argument.listOf(String::class.java))

        // then
        coVerifyOnce {
            dataProvider.searchNames(
                tenant = Defaults.TENANT,
                dataType = DataType.EVENTS,
                filters = emptyList(),
                size = 20
            )
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(names)

        }
        confirmVerified(dataProvider)
    }

    @Test
    internal fun `should search names for the meters with filter and page size`() {
        // given
        val names = listOf("event-1", "event-2")
        val request = HttpRequest.GET<List<String>>("/meters/names?filter=filter-1%2Cfilter-2&size=12")
        coEvery { dataProvider.searchNames(any(), any(), any(), any()) } returns names

        // when
        val response = httpClient.toBlocking().exchange(request, Argument.listOf(String::class.java))

        // then
        coVerifyOnce {
            dataProvider.searchNames(
                tenant = Defaults.TENANT,
                dataType = DataType.METERS,
                filters = listOf("filter-1", "filter-2"),
                size = 12
            )
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(names)

        }
        confirmVerified(dataProvider)
    }

    @Test
    internal fun `should fail searching names for the meters when the page size is negative`() {
        // given
        val request = HttpRequest.GET<List<String>>("/meters/names?size=-12")

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(request, Argument.listOf(String::class.java))
        }.response

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)

        }
        confirmVerified(dataProvider)
    }

    @Test
    internal fun `should fail searching names for the meters when the page size is too high`() {
        // given
        val request = HttpRequest.GET<List<String>>("/meters/names?size=101")

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(request, Argument.listOf(String::class.java))
        }.response

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)

        }
        confirmVerified(dataProvider)
    }

    @Test
    internal fun `should search field names for the events`() {
        // given
        val fields = listOf(DataField("1", DataFieldType.NUMBER), DataField("2", DataFieldType.NUMBER, "SECONDS"))
        val request = HttpRequest.GET<List<String>>("/events/fields")
        coEvery { dataProvider.listFields(any(), any()) } returns fields

        // when
        val response = httpClient.toBlocking().exchange(request, Argument.listOf(DataField::class.java))

        // then
        coVerifyOnce {
            dataProvider.listFields(
                tenant = Defaults.TENANT,
                dataType = DataType.EVENTS
            )
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(fields)

        }
        confirmVerified(dataProvider)
    }

    @Test
    internal fun `should search tags for the events without filter`() {
        // given
        val tags = mapOf("tag-1" to listOf("value-1", "value-2"), "tag-2" to listOf("value-3", "value-4"))
        val request = HttpRequest.GET<List<String>>("/events/tags")
        coEvery { dataProvider.searchTagsAndValues(any(), any(), any(), any()) } returns tags

        // when
        val response = httpClient.toBlocking()
            .exchange(request, Argument.mapOf(Argument.of(String::class.java), Argument.listOf(String::class.java)))

        // then
        coVerifyOnce {
            dataProvider.searchTagsAndValues(
                tenant = Defaults.TENANT,
                dataType = DataType.EVENTS,
                filters = emptyList(),
                size = 20
            )
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(tags)

        }
        confirmVerified(dataProvider)
    }

    @Test
    internal fun `should search tags for the meters with filter and page size`() {
        // given
        val tags = mapOf("tag-1" to listOf("value-1", "value-2"), "tag-2" to listOf("value-3", "value-4"))
        val request = HttpRequest.GET<List<String>>("/meters/tags?filter=filter-1%2Cfilter-2&size=12")
        coEvery { dataProvider.searchTagsAndValues(any(), any(), any(), any()) } returns tags

        // when
        val response = httpClient.toBlocking()
            .exchange(request, Argument.mapOf(Argument.of(String::class.java), Argument.listOf(String::class.java)))

        // then
        coVerifyOnce {
            dataProvider.searchTagsAndValues(
                tenant = Defaults.TENANT,
                dataType = DataType.METERS,
                filters = listOf("filter-1", "filter-2"),
                size = 12
            )
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(tags)

        }
        confirmVerified(dataProvider)
    }

    @Test
    internal fun `should fail searching tags for the meters when page size is negative`() {
        // given
        val request = HttpRequest.GET<List<String>>("/meters/tags?size=-12")

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking()
                .exchange(request, Argument.mapOf(Argument.of(String::class.java), Argument.listOf(String::class.java)))
        }.response

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)

        }
        confirmVerified(dataProvider)
    }

    @Test
    internal fun `should fail searching tags for the meters when page size is too high`() {
        // given
        val request = HttpRequest.GET<List<String>>("/meters/tags?size=101")

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking()
                .exchange(request, Argument.mapOf(Argument.of(String::class.java), Argument.listOf(String::class.java)))
        }.response

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)

        }
        confirmVerified(dataProvider)
    }

    @Test
    internal fun `should search data series with the default parameters`() {
        // given
        val dataSeries = DataSeries(
            displayName = "NewData",
            dataType = DataType.EVENTS,
            valueName = "my-event",
            color = "#ff0000",
            filters = emptySet(),
            fieldName = "dataSeriesFieldName",
            aggregationOperation = null,
            timeframeUnit = Duration.ofHours(1),
        )
        val request = HttpRequest.GET<Page<DataSeries>>("/")
        coEvery {
            dataSeriesService.searchDataSeries(
                tenant = Defaults.TENANT,
                username = Defaults.USER,
                filters = emptyList(),
                sort = null,
                page = 0,
                size = 20
            )
        } returns Page(0, 1, 1, listOf(dataSeries))

        // when
        val response = httpClient.toBlocking().exchange(request, Argument.of(Page::class.java, DataSeries::class.java))

        //then
        coVerifyOnce {
            dataSeriesService.searchDataSeries(
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
            transform("body") { it.body() }.isDataClassEqualTo(Page(0, 1, 1, listOf(dataSeries)))
        }
    }

    @Test
    internal fun `should fail for negative page value`() {
        // given
        val request = HttpRequest.GET<Page<DataSeries>>("/?page=-2")

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(request, Argument.of(Page::class.java, DataSeries::class.java))
        }.response

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @Test
    internal fun `should fail for negative size value`() {
        // given
        val request = HttpRequest.GET<Page<DataSeries>>("/?size=-200")

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(request, Argument.of(Page::class.java, DataSeries::class.java))
        }.response

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @Test
    internal fun `should search for data series with filter and default sorting`() {
        // given
        val dataSeries = DataSeries(
            displayName = "NewData",
            dataType = DataType.EVENTS,
            valueName = "my-event",
            color = "#ff0000",
            filters = emptySet(),
            fieldName = "filter-2",
            aggregationOperation = null,
            timeframeUnit = Duration.ofHours(1),
        )
        val dataSeries2 = DataSeries(
            displayName = "New-Data2",
            dataType = DataType.EVENTS,
            valueName = "my-event",
            color = "#FFF",
            filters = emptySet(),
            fieldName = "foo",
            aggregationOperation = null,
            timeframeUnit = Duration.ofHours(2),
        )
        val request = HttpRequest.GET<Page<DataSeries>>("?filter=foo,filter-2&size=7")
        coEvery {
            dataSeriesService.searchDataSeries(
                tenant = Defaults.TENANT,
                username = Defaults.USER,
                filters = listOf("foo", "filter-2"),
                sort = null,
                page = 0,
                size = 7
            )
        } returns Page(0, 1, 2, listOf(dataSeries, dataSeries2))

        // when
        val response = httpClient.toBlocking().exchange(request, Argument.of(Page::class.java, DataSeries::class.java))

        // then
        coVerifyOnce {
            dataSeriesService.searchDataSeries(
                tenant = Defaults.TENANT,
                username = Defaults.USER,
                filters = listOf("foo", "filter-2"),
                sort = null,
                page = 0,
                size = 7
            )
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(Page(0, 1, 2, listOf(dataSeries, dataSeries2)))
        }
    }

    @Test
    internal fun `should search for data series with specified sorting`() {
        // given
        val dataSeries = DataSeries(
            displayName = "NewData",
            dataType = DataType.EVENTS,
            valueName = "my-event",
            color = "#ff0000",
            filters = emptySet(),
            fieldName = "filter-2",
            aggregationOperation = null,
            timeframeUnit = Duration.ofHours(1),
        )
        val dataSeries2 = DataSeries(
            displayName = "New-Data2",
            dataType = DataType.EVENTS,
            valueName = "my-other-event",
            color = "Blue",
            filters = emptySet(),
            fieldName = "foo",
            aggregationOperation = null,
            timeframeUnit = Duration.ofHours(2),
        )
        val request = HttpRequest.GET<Page<DataSeries>>("?sort=color&size=7")
        coEvery {
            dataSeriesService.searchDataSeries(
                tenant = Defaults.TENANT,
                username = Defaults.USER,
                filters = emptyList(),
                sort = "color",
                page = 0,
                size = 7
            )
        } returns Page(0, 1, 2, listOf(dataSeries2, dataSeries))

        // when
        val response = httpClient.toBlocking().exchange(request, Argument.of(Page::class.java, DataSeries::class.java))

        // then
        coVerifyOnce {
            dataSeriesService.searchDataSeries(
                tenant = Defaults.TENANT,
                username = Defaults.USER,
                filters = emptyList(),
                sort = "color",
                page = 0,
                size = 7
            )
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(Page(0, 1, 2, listOf(dataSeries2, dataSeries)))
        }
    }
}