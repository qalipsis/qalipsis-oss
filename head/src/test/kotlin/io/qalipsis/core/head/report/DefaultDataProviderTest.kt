package io.qalipsis.core.head.report

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.qalipsis.api.report.DataField
import io.qalipsis.api.report.EventProvider
import io.qalipsis.api.report.MeterProvider
import io.qalipsis.api.report.query.QueryDescription
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.verifyOnce
import org.junit.jupiter.api.Test

@WithMockk
internal class DefaultDataProviderTest {

    @MockK
    private lateinit var eventProvider: EventProvider

    @MockK
    private lateinit var meterProvider: MeterProvider

    @Test
    internal fun `should return empty lists and maps when no event provider is set`() {
        // given
        val dataProvider = DefaultDataProvider(null, meterProvider)
        val queryDescription = mockk<QueryDescription>()

        // then
        assertThat(dataProvider.searchNames("my-tenant", DataType.EVENTS, emptySet(), 10)).isEmpty()
        assertThat(dataProvider.listFields("my-tenant", DataType.EVENTS)).isEmpty()
        assertThat(dataProvider.searchTagsAndValues("my-tenant", DataType.EVENTS, emptySet(), 10)).isEmpty()
        assertThat(dataProvider.createQuery("my-tenant", DataType.EVENTS, queryDescription)).isEmpty()

        confirmVerified(eventProvider, meterProvider)
    }

    @Test
    internal fun `should return names of events`() {
        // given
        val dataProvider = DefaultDataProvider(eventProvider, meterProvider)
        val names = listOf("1", "2")
        every { eventProvider.searchNames(any(), any(), any()) } returns names
        val filters = listOf("filter-1", "filter-2")

        // then
        assertThat(dataProvider.searchNames("my-tenant", DataType.EVENTS, filters, 10)).isEqualTo(names)
        verifyOnce { eventProvider.searchNames("my-tenant", refEq(filters), 10) }

        confirmVerified(eventProvider, meterProvider)
    }

    @Test
    internal fun `should return fields names of events`() {
        // given
        val dataProvider = DefaultDataProvider(eventProvider, meterProvider)
        val fields = listOf(DataField("1", true), DataField("2", true, "SECONDS"))
        every { eventProvider.listFields(any()) } returns fields

        // then
        assertThat(dataProvider.listFields("my-tenant", DataType.EVENTS)).isEqualTo(fields)
        verifyOnce { eventProvider.listFields("my-tenant") }

        confirmVerified(eventProvider, meterProvider)
    }

    @Test
    internal fun `should return tags names and values of events`() {
        // given
        val dataProvider = DefaultDataProvider(eventProvider, meterProvider)
        val tagsAndValues = mapOf("1" to listOf("val-1", "val-2"), "2" to listOf("val-3", "val-4"))
        every { eventProvider.searchTagsAndValues(any(), any(), any()) } returns tagsAndValues
        val filters = listOf("filter-1", "filter-2")

        // then
        assertThat(dataProvider.searchTagsAndValues("my-tenant", DataType.EVENTS, filters, 10)).isEqualTo(tagsAndValues)
        verifyOnce { eventProvider.searchTagsAndValues("my-tenant", refEq(filters), 10) }

        confirmVerified(eventProvider, meterProvider)
    }

    @Test
    internal fun `should return query for events`() {
        // given
        val dataProvider = DefaultDataProvider(eventProvider, meterProvider)
        val queryDescription = mockk<QueryDescription>()
        val query = "the query"
        every { eventProvider.createQuery(any(), any()) } returns query

        // then
        assertThat(dataProvider.createQuery("my-tenant", DataType.EVENTS, queryDescription)).isEqualTo(query)
        verifyOnce { eventProvider.createQuery("my-tenant", refEq(queryDescription)) }

        confirmVerified(eventProvider, meterProvider)
    }

    @Test
    internal fun `should return empty lists and maps when no meter provider is set`() {
        // given
        val dataProvider = DefaultDataProvider(eventProvider, null)
        val queryDescription = mockk<QueryDescription>()

        // then
        assertThat(dataProvider.searchNames("my-tenant", DataType.METERS, emptySet(), 10)).isEmpty()
        assertThat(dataProvider.listFields("my-tenant", DataType.METERS)).isEmpty()
        assertThat(dataProvider.searchTagsAndValues("my-tenant", DataType.METERS, emptySet(), 10)).isEmpty()
        assertThat(dataProvider.createQuery("my-tenant", DataType.METERS, queryDescription)).isEmpty()

        confirmVerified(eventProvider, meterProvider)
    }

    @Test
    internal fun `should return names of meters`() {
        // given
        val dataProvider = DefaultDataProvider(eventProvider, meterProvider)
        val names = listOf("1", "2")
        every { meterProvider.searchNames(any(), any(), any()) } returns names
        val filters = listOf("filter-1", "filter-2")

        // then
        assertThat(dataProvider.searchNames("my-tenant", DataType.METERS, filters, 10)).isEqualTo(names)
        verifyOnce { meterProvider.searchNames("my-tenant", refEq(filters), 10) }

        confirmVerified(eventProvider, meterProvider)
    }

    @Test
    internal fun `should return fields names of meters`() {
        // given
        val dataProvider = DefaultDataProvider(eventProvider, meterProvider)
        val fields = listOf(DataField("1", true), DataField("2", true, "SECONDS"))
        every { meterProvider.listFields(any()) } returns fields

        // then
        assertThat(dataProvider.listFields("my-tenant", DataType.METERS)).isEqualTo(fields)
        verifyOnce { meterProvider.listFields("my-tenant") }

        confirmVerified(eventProvider, meterProvider)

    }

    @Test
    internal fun `should return tags names and values of meters`() {
        // given
        val dataProvider = DefaultDataProvider(eventProvider, meterProvider)
        val tagsAndValues = mapOf("1" to listOf("val-1", "val-2"), "2" to listOf("val-3", "val-4"))
        every { meterProvider.searchTagsAndValues(any(), any(), any()) } returns tagsAndValues
        val filters = listOf("filter-1", "filter-2")

        // then
        assertThat(dataProvider.searchTagsAndValues("my-tenant", DataType.METERS, filters, 10)).isEqualTo(tagsAndValues)
        verifyOnce { meterProvider.searchTagsAndValues("my-tenant", refEq(filters), 10) }

        confirmVerified(eventProvider, meterProvider)
    }

    @Test
    internal fun `should return query for meters`() {
        // given
        val dataProvider = DefaultDataProvider(eventProvider, meterProvider)
        val queryDescription = mockk<QueryDescription>()
        val query = "the query"
        every { meterProvider.createQuery(any(), any()) } returns query

        // then
        assertThat(dataProvider.createQuery("my-tenant", DataType.METERS, queryDescription)).isEqualTo(query)
        verifyOnce { meterProvider.createQuery("my-tenant", refEq(queryDescription)) }

        confirmVerified(eventProvider, meterProvider)
    }
}