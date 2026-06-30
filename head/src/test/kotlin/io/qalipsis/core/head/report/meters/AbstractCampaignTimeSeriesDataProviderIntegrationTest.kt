/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.report.meters

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.prop
import io.mockk.mockk
import io.qalipsis.api.query.AggregationQueryExecutionContext
import io.qalipsis.api.query.DataRetrievalQueryExecutionContext
import io.qalipsis.api.query.Page
import io.qalipsis.api.report.TimeSeriesDataProvider
import io.qalipsis.api.report.TimeSeriesMeter
import io.qalipsis.core.feedbacks.CampaignMetersFeedback
import io.qalipsis.core.head.communication.CampaignMeterFeedbackListener
import io.qalipsis.core.meters.CampaignMeterSnapshot
import io.qalipsis.test.coroutines.TestDispatcherProvider
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

@Timeout(20, unit = TimeUnit.SECONDS)
internal abstract class AbstractCampaignTimeSeriesDataProviderIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Inject
    protected lateinit var feedbackListener: CampaignMeterFeedbackListener

    @Inject
    protected lateinit var dataProvider: TimeSeriesDataProvider

    @BeforeEach
    fun setUp() = testDispatcherProvider.run {
        setUpTest()
    }

    protected abstract suspend fun setUpTest()

    @Test
    internal fun `should publish campaign-scope counter and retrieve it`() = testDispatcherProvider.run {
        val now = Instant.parse("2024-01-10T12:00:00Z")
        feedbackListener.notify(
            CampaignMetersFeedback(
                meters = listOf(
                    CampaignMeterSnapshot(
                        name = "requests-count",
                        tags = """{"scope":"campaign"}""",
                        timestampEpochMs = now.toEpochMilli(),
                        tenant = "t1",
                        campaign = "camp1",
                        scenario = "sc1",
                        type = "counter",
                        count = 42.0
                    )
                )
            )
        )

        val result = dataProvider.retrieveCampaignMeters("t1", listOf("camp1"))
        assertThat(result).hasSize(1)
        assertThat(result[0]).prop(TimeSeriesMeter::name).isEqualTo("requests-count")
        assertThat(result[0]).prop(TimeSeriesMeter::type).isEqualTo("counter")
        assertThat(result[0]).prop(TimeSeriesMeter::campaign).isEqualTo("camp1")
        assertThat(result[0]).prop(TimeSeriesMeter::scenario).isEqualTo("sc1")
        assertThat(result[0]).prop(TimeSeriesMeter::count).isEqualTo(42L)
        assertThat(result[0]).prop(TimeSeriesMeter::tags).isEqualTo(mapOf("scope" to "campaign"))
    }

    @Test
    internal fun `should publish campaign-scope gauge and retrieve it`() = testDispatcherProvider.run {
        val now = Instant.parse("2024-01-10T13:00:00Z")
        feedbackListener.notify(
            CampaignMetersFeedback(
                meters = listOf(
                    CampaignMeterSnapshot(
                        name = "cpu-usage",
                        tags = """{"scope":"campaign"}""",
                        timestampEpochMs = now.toEpochMilli(),
                        tenant = "t1",
                        campaign = "camp1",
                        scenario = "sc1",
                        type = "gauge",
                        value = 75.5
                    )
                )
            )
        )

        val result = dataProvider.retrieveCampaignMeters("t1", listOf("camp1"))
        assertThat(result).hasSize(1)
        assertThat(result[0]).prop(TimeSeriesMeter::name).isEqualTo("cpu-usage")
        assertThat(result[0]).prop(TimeSeriesMeter::type).isEqualTo("gauge")
        assertThat(result[0]).prop(TimeSeriesMeter::value).transform { it!!.compareTo(BigDecimal("75.5")) }
            .isEqualTo(0)
    }

    @Test
    internal fun `should publish timer and retrieve it with duration fields`() = testDispatcherProvider.run {
        // Values in microseconds; convertJdbcRow multiplies by 1000 to get nanoseconds.
        // 1_000_000 µs → Duration.ofSeconds(1); 100_000 µs → Duration.ofMillis(100); 500_000 µs → Duration.ofMillis(500)
        val now = Instant.parse("2024-01-10T14:00:00Z")
        feedbackListener.notify(
            CampaignMetersFeedback(
                meters = listOf(
                    CampaignMeterSnapshot(
                        name = "response-time",
                        tags = """{"scope":"campaign"}""",
                        timestampEpochMs = now.toEpochMilli(),
                        tenant = "t1",
                        campaign = "camp1",
                        scenario = "sc1",
                        type = "timer",
                        count = 10.0,
                        sum = 1_000_000.0,
                        mean = 100_000.0,
                        max = 500_000.0,
                        unit = "MICROSECONDS"
                    )
                )
            )
        )

        val result = dataProvider.retrieveCampaignMeters("t1", listOf("camp1"))
        assertThat(result).hasSize(1)
        assertThat(result[0]).prop(TimeSeriesMeter::name).isEqualTo("response-time")
        assertThat(result[0]).prop(TimeSeriesMeter::type).isEqualTo("timer")
        assertThat(result[0]).prop(TimeSeriesMeter::count).isEqualTo(10L)
        assertThat(result[0]).prop(TimeSeriesMeter::sumDuration).isEqualTo(Duration.ofSeconds(1))
        assertThat(result[0]).prop(TimeSeriesMeter::meanDuration).isEqualTo(Duration.ofMillis(100))
        assertThat(result[0]).prop(TimeSeriesMeter::maxDuration).isEqualTo(Duration.ofMillis(500))
        assertThat(result[0]).prop(TimeSeriesMeter::value).isNull()
    }

    @Test
    internal fun `should filter out meters without campaign scope`() = testDispatcherProvider.run {
        val now = Instant.parse("2024-01-10T15:00:00Z")
        feedbackListener.notify(
            CampaignMetersFeedback(
                meters = listOf(
                    CampaignMeterSnapshot(
                        name = "scoped-gauge",
                        tags = """{"scope":"campaign"}""",
                        timestampEpochMs = now.toEpochMilli(),
                        tenant = "t1",
                        campaign = "camp1",
                        scenario = "sc1",
                        type = "gauge",
                        value = 1.0
                    )
                )
            )
        )

        val result = dataProvider.retrieveCampaignMeters("t1", listOf("camp1"))
        assertThat(result).hasSize(1)
        assertThat(result[0]).prop(TimeSeriesMeter::name).isEqualTo("scoped-gauge")
    }

    @Test
    internal fun `should filter retrieveCampaignMeters by scenario names`() = testDispatcherProvider.run {
        val now = Instant.parse("2024-01-10T16:00:00Z")
        listOf("sc1", "sc2", "sc3").forEach { scenario ->
            feedbackListener.notify(
                CampaignMetersFeedback(
                    meters = listOf(
                        CampaignMeterSnapshot(
                            name = "gauge",
                            tags = """{"scope":"campaign"}""",
                            timestampEpochMs = now.toEpochMilli(),
                            tenant = "t1",
                            campaign = "camp1",
                            scenario = scenario,
                            type = "gauge",
                            value = 1.0
                        )
                    )
                )
            )
        }

        val result = dataProvider.retrieveCampaignMeters("t1", listOf("camp1"), listOf("sc1", "sc3"))
        assertThat(result).hasSize(2)
        assertThat(result.map { it.scenario }).isEqualTo(listOf("sc1", "sc3"))
    }

    @Test
    internal fun `should return empty when tenant or campaign not found`() = testDispatcherProvider.run {
        val now = Instant.parse("2024-01-10T17:00:00Z")
        feedbackListener.notify(
            CampaignMetersFeedback(
                meters = listOf(
                    CampaignMeterSnapshot(
                        name = "gauge",
                        tags = """{"scope":"campaign"}""",
                        timestampEpochMs = now.toEpochMilli(),
                        tenant = "t1",
                        campaign = "camp1",
                        scenario = "sc1",
                        type = "gauge",
                        value = 1.0
                    )
                )
            )
        )

        assertThat(dataProvider.retrieveCampaignMeters("other-tenant", listOf("camp1"))).isEmpty()
        assertThat(dataProvider.retrieveCampaignMeters("t1", listOf("other-campaign"))).isEmpty()
    }

    @Test
    internal fun `should return zero from retrieveUsedStorage`() = testDispatcherProvider.run {
        assertThat(dataProvider.retrieveUsedStorage("any-tenant")).isEqualTo(0L)
    }

    @Test
    internal fun `should return empty map from executeAggregations`() = testDispatcherProvider.run {
        val result = dataProvider.executeAggregations(
            emptyMap(),
            mockk<AggregationQueryExecutionContext>(relaxed = true)
        )
        assertThat(result).isEqualTo(emptyMap())
    }

    @Test
    internal fun `should return empty map from retrieveRecords`() = testDispatcherProvider.run {
        val result = dataProvider.retrieveRecords(
            emptyMap(),
            mockk<DataRetrievalQueryExecutionContext>(relaxed = true)
        )
        assertThat(result).isEqualTo(emptyMap<String, Page<*>>())
    }
}
