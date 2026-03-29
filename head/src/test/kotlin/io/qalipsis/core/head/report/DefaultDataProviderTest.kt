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

package io.qalipsis.core.head.report

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.qalipsis.api.query.QueryDescription
import io.qalipsis.api.report.DataField
import io.qalipsis.api.report.DataFieldType
import io.qalipsis.api.report.EventMetadataProvider
import io.qalipsis.api.report.MeterMetadataProvider
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class DefaultDataProviderTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var eventProvider: EventMetadataProvider

    @MockK
    private lateinit var meterProvider: MeterMetadataProvider

    @Test
    internal fun `should return empty lists and maps when no event provider is set`() = testDispatcherProvider.runTest {
        // given
        val dataProvider = DefaultDataProvider(null, meterProvider)
        val queryDescription = mockk<QueryDescription>()

        // then
        assertThat(dataProvider.searchNames("my-tenant", DataType.EVENTS, "my-campaign", emptySet(), 10)).isEmpty()
        assertThat(dataProvider.listFields("my-tenant", DataType.EVENTS, "my-event")).isEmpty()
        assertThat(dataProvider.searchTagsAndValues("my-tenant", DataType.EVENTS, "my-event", emptySet(), 10)).isEmpty()
        assertThat(dataProvider.createQuery("my-tenant", DataType.EVENTS, queryDescription)).isEmpty()

        confirmVerified(eventProvider, meterProvider)
    }

    @Test
    internal fun `should return names of events`() = testDispatcherProvider.runTest {
        // given
        val dataProvider = DefaultDataProvider(eventProvider, meterProvider)
        val names = listOf("1", "2")
        val campaign = "my-campaign"
        coEvery { eventProvider.searchNames(any(), any(), any(), any()) } returns names
        val filters = listOf("filter-1", "filter-2")

        // then
        assertThat(dataProvider.searchNames("my-tenant", DataType.EVENTS, campaign, filters, 10)).isEqualTo(names)
        coVerifyOnce { eventProvider.searchNames("my-tenant", campaign, refEq(filters), 10) }

        confirmVerified(eventProvider, meterProvider)
    }

    @Test
    internal fun `should return fields names of events`() = testDispatcherProvider.runTest {
        // given
        val dataProvider = DefaultDataProvider(eventProvider, meterProvider)
        val eventName = "my-event"
        val fields = listOf(DataField("1", DataFieldType.NUMBER), DataField("2", DataFieldType.NUMBER, "SECONDS"))
        coEvery { eventProvider.listFields(any(), any()) } returns fields

        // then
        assertThat(dataProvider.listFields("my-tenant", DataType.EVENTS, eventName)).isEqualTo(fields)
        coVerifyOnce { eventProvider.listFields("my-tenant", eventName) }

        confirmVerified(eventProvider, meterProvider)
    }

    @Test
    internal fun `should return tags names and values of events`() = testDispatcherProvider.runTest {
        // given
        val dataProvider = DefaultDataProvider(eventProvider, meterProvider)
        val eventName = "my-event"
        val tagsAndValues = mapOf("1" to listOf("val-1", "val-2"), "2" to listOf("val-3", "val-4"))
        coEvery { eventProvider.searchTagsAndValues(any(), any(), any(), any()) } returns tagsAndValues
        val filters = listOf("filter-1", "filter-2")

        // then
        assertThat(dataProvider.searchTagsAndValues("my-tenant", DataType.EVENTS, eventName, filters, 10)).isEqualTo(
            tagsAndValues
        )
        coVerifyOnce { eventProvider.searchTagsAndValues("my-tenant", eventName, refEq(filters), 10) }

        confirmVerified(eventProvider, meterProvider)
    }

    @Test
    internal fun `should return query for events`() = testDispatcherProvider.runTest {
        // given
        val dataProvider = DefaultDataProvider(eventProvider, meterProvider)
        val queryDescription = mockk<QueryDescription>()
        val query = "the query"
        coEvery { eventProvider.createQuery(any(), any()) } returns query

        // then
        assertThat(dataProvider.createQuery("my-tenant", DataType.EVENTS, queryDescription)).isEqualTo(query)
        coVerifyOnce { eventProvider.createQuery("my-tenant", refEq(queryDescription)) }

        confirmVerified(eventProvider, meterProvider)
    }

    @Test
    internal fun `should return query for events when default tenant is passed in`() = testDispatcherProvider.runTest {
        // given
        val dataProvider = DefaultDataProvider(eventProvider, meterProvider)
        val queryDescription = mockk<QueryDescription>()
        val query = "the query"
        coEvery { eventProvider.createQuery(any(), any()) } returns query

        // then
        assertThat(dataProvider.createQuery("_qalipsis_ten_", DataType.EVENTS, queryDescription)).isEqualTo(query)
        coVerifyOnce { eventProvider.createQuery(null, refEq(queryDescription)) }

        confirmVerified(eventProvider, meterProvider)
    }

    @Test
    internal fun `should return empty lists and maps when no meter provider is set`() = testDispatcherProvider.runTest {
        // given
        val dataProvider = DefaultDataProvider(eventProvider, null)
        val queryDescription = mockk<QueryDescription>()

        // then
        assertThat(dataProvider.searchNames("my-tenant", DataType.METERS, "my-campaign", emptySet(), 10)).isEmpty()
        assertThat(dataProvider.listFields("my-tenant", DataType.METERS, "my-meter")).isEmpty()
        assertThat(dataProvider.searchTagsAndValues("my-tenant", DataType.METERS, "my-meter", emptySet(), 10)).isEmpty()
        assertThat(dataProvider.createQuery("my-tenant", DataType.METERS, queryDescription)).isEmpty()

        confirmVerified(eventProvider, meterProvider)
    }

    @Test
    internal fun `should return names of meters`() = testDispatcherProvider.runTest {
        // given
        val dataProvider = DefaultDataProvider(eventProvider, meterProvider)
        val campaign = "my-campaign"
        val names = listOf("1", "2")
        coEvery { meterProvider.searchNames(any(), any(), any(), any()) } returns names
        val filters = listOf("filter-1", "filter-2")

        // then
        assertThat(dataProvider.searchNames("my-tenant", DataType.METERS, campaign, filters, 10)).isEqualTo(names)
        coVerifyOnce { meterProvider.searchNames("my-tenant", campaign, refEq(filters), 10) }

        confirmVerified(eventProvider, meterProvider)
    }

    @Test
    internal fun `should return fields names of meters`() = testDispatcherProvider.runTest {
        // given
        val dataProvider = DefaultDataProvider(eventProvider, meterProvider)
        val meterName = "my-meter"
        val fields = listOf(DataField("1", DataFieldType.NUMBER), DataField("2", DataFieldType.NUMBER, "SECONDS"))
        coEvery { meterProvider.listFields(any(), any()) } returns fields

        // then
        assertThat(dataProvider.listFields("my-tenant", DataType.METERS, meterName)).isEqualTo(fields)
        coVerifyOnce { meterProvider.listFields("my-tenant", meterName) }

        confirmVerified(eventProvider, meterProvider)

    }

    @Test
    internal fun `should return tags names and values of meters`() = testDispatcherProvider.runTest {
        // given
        val dataProvider = DefaultDataProvider(eventProvider, meterProvider)
        val meterName = "my-meter"
        val tagsAndValues = mapOf("1" to listOf("val-1", "val-2"), "2" to listOf("val-3", "val-4"))
        coEvery { meterProvider.searchTagsAndValues(any(), any(), any(), any()) } returns tagsAndValues
        val filters = listOf("filter-1", "filter-2")

        // then
        assertThat(dataProvider.searchTagsAndValues("my-tenant", DataType.METERS, meterName, filters, 10)).isEqualTo(
            tagsAndValues
        )
        coVerifyOnce { meterProvider.searchTagsAndValues("my-tenant", meterName, refEq(filters), 10) }

        confirmVerified(eventProvider, meterProvider)
    }

    @Test
    internal fun `should return query for meters`() = testDispatcherProvider.runTest {
        // given
        val dataProvider = DefaultDataProvider(eventProvider, meterProvider)
        val queryDescription = mockk<QueryDescription>()
        val query = "the query"
        coEvery { meterProvider.createQuery(any(), any()) } returns query

        // then
        assertThat(dataProvider.createQuery("my-tenant", DataType.METERS, queryDescription)).isEqualTo(query)
        coVerifyOnce { meterProvider.createQuery("my-tenant", refEq(queryDescription)) }

        confirmVerified(eventProvider, meterProvider)
    }

    @Test
    internal fun `should return query for meter when default tenant is passed in`() = testDispatcherProvider.runTest {
        // given
        val dataProvider = DefaultDataProvider(eventProvider, meterProvider)
        val queryDescription = mockk<QueryDescription>()
        val query = "the query"
        coEvery { meterProvider.createQuery(any(), any()) } returns query

        // then
        assertThat(dataProvider.createQuery("_qalipsis_ten_", DataType.METERS, queryDescription)).isEqualTo(query)
        coVerifyOnce { meterProvider.createQuery(null, refEq(queryDescription)) }

        confirmVerified(eventProvider, meterProvider)
    }

}