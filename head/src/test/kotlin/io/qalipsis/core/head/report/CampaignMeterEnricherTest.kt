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
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.impl.annotations.MockK
import io.qalipsis.api.report.TimeSeriesDataProvider
import io.qalipsis.api.report.TimeSeriesMeter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

@WithMockk
internal class CampaignMeterEnricherTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var timeSeriesDataProvider: TimeSeriesDataProvider

    private fun meter(
        name: String,
        scenario: String? = null,
        stepTag: String? = null
    ): TimeSeriesMeter = TimeSeriesMeter(
        name = name,
        timestamp = Instant.now(),
        type = "counter",
        campaign = "camp-1",
        scenario = scenario,
        tags = stepTag?.let { mapOf("step" to it) }
    )

    @Test
    internal fun `should return empty distribution when no time series data provider is present`() =
        testDispatcherProvider.runTest {
            // given
            val subject = CampaignMeterEnricher(null)

            // when
            val result = subject.distribute("my-tenant", listOf("camp-1"), listOf("scenario-1"))

            // then
            assertThat(result["camp-1"]!!.campaignMeters).isEmpty()
            assertThat(result["camp-1"]!!.byScenario).isEqualTo(emptyMap())
            assertThat(result["camp-1"]!!.byScenarioAndStep).isEqualTo(emptyMap())
        }

    @Test
    internal fun `should place meters with null scenario into campaignMeters`() = testDispatcherProvider.runTest {
        // given
        val subject = CampaignMeterEnricher(timeSeriesDataProvider)
        val campaignMeter1 = meter("cpu", scenario = null)
        val campaignMeter2 = meter("mem", scenario = null)
        coEvery {
            timeSeriesDataProvider.retrieveCampaignMeters("my-tenant", listOf("camp-1"), listOf("scenario-1"))
        } returns listOf(campaignMeter1, campaignMeter2)

        // when
        val result = subject.distribute("my-tenant", listOf("camp-1"), listOf("scenario-1"))

        // then
        assertThat(result["camp-1"]!!.campaignMeters).containsExactly(campaignMeter1, campaignMeter2)
        assertThat(result["camp-1"]!!.byScenario).isEqualTo(emptyMap())
        assertThat(result["camp-1"]!!.byScenarioAndStep).isEqualTo(emptyMap())
        coVerify { timeSeriesDataProvider.retrieveCampaignMeters("my-tenant", listOf("camp-1"), listOf("scenario-1")) }
        confirmVerified(timeSeriesDataProvider)
    }

    @Test
    internal fun `should place meters with scenario but no step tag into byScenario`() =
        testDispatcherProvider.runTest {
            // given
            val subject = CampaignMeterEnricher(timeSeriesDataProvider)
            val scenarioMeter = meter("rps", scenario = "sc-1", stepTag = null)
            coEvery {
                timeSeriesDataProvider.retrieveCampaignMeters("my-tenant", listOf("camp-1"), listOf("sc-1"))
            } returns listOf(scenarioMeter)

            // when
            val result = subject.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))

            // then
            assertThat(result["camp-1"]!!.campaignMeters).isEmpty()
            assertThat(result["camp-1"]!!.byScenario).isEqualTo(mapOf("sc-1" to listOf(scenarioMeter)))
            assertThat(result["camp-1"]!!.byScenarioAndStep).isEqualTo(emptyMap())
            coVerify { timeSeriesDataProvider.retrieveCampaignMeters("my-tenant", listOf("camp-1"), listOf("sc-1")) }
            confirmVerified(timeSeriesDataProvider)
        }

    @Test
    internal fun `should place meters with scenario and step tag into byScenarioAndStep`() =
        testDispatcherProvider.runTest {
            // given
            val subject = CampaignMeterEnricher(timeSeriesDataProvider)
            val stepMeter = meter("latency", scenario = "sc-1", stepTag = "step-A")
            coEvery {
                timeSeriesDataProvider.retrieveCampaignMeters("my-tenant", listOf("camp-1"), listOf("sc-1"))
            } returns listOf(stepMeter)

            // when
            val result = subject.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))

            // then
            assertThat(result["camp-1"]!!.campaignMeters).isEmpty()
            assertThat(result["camp-1"]!!.byScenario).isEqualTo(emptyMap())
            assertThat(result["camp-1"]!!.byScenarioAndStep).isEqualTo(
                mapOf(
                    "sc-1" to mapOf(
                        "step-A" to listOf(
                            stepMeter
                        )
                    )
                )
            )
            coVerify { timeSeriesDataProvider.retrieveCampaignMeters("my-tenant", listOf("camp-1"), listOf("sc-1")) }
            confirmVerified(timeSeriesDataProvider)
        }

    @Test
    internal fun `should correctly partition mixed meters to all three buckets`() = testDispatcherProvider.runTest {
        // given
        val subject = CampaignMeterEnricher(timeSeriesDataProvider)
        val campaignMeter = meter("cpu", scenario = null)
        val scenarioMeter = meter("rps", scenario = "sc-1", stepTag = null)
        val stepMeter1 = meter("latency", scenario = "sc-1", stepTag = "step-A")
        val stepMeter2 = meter("errors", scenario = "sc-2", stepTag = "step-B")
        coEvery {
            timeSeriesDataProvider.retrieveCampaignMeters("my-tenant", listOf("camp-1"), listOf("sc-1", "sc-2"))
        } returns listOf(campaignMeter, scenarioMeter, stepMeter1, stepMeter2)

        // when
        val result = subject.distribute("my-tenant", listOf("camp-1"), listOf("sc-1", "sc-2"))

        // then
        assertThat(result["camp-1"]!!.campaignMeters).containsExactly(campaignMeter)
        assertThat(result["camp-1"]!!.byScenario).isEqualTo(mapOf("sc-1" to listOf(scenarioMeter)))
        assertThat(result["camp-1"]!!.byScenarioAndStep).isEqualTo(
            mapOf(
                "sc-1" to mapOf("step-A" to listOf(stepMeter1)),
                "sc-2" to mapOf("step-B" to listOf(stepMeter2))
            )
        )
        coVerify {
            timeSeriesDataProvider.retrieveCampaignMeters(
                "my-tenant",
                listOf("camp-1"),
                listOf("sc-1", "sc-2")
            )
        }
        confirmVerified(timeSeriesDataProvider)
    }

    @Test
    internal fun `scenarioMeters should return meters for the requested scenario`() =
        testDispatcherProvider.runTest {
            // given
            val subject = CampaignMeterEnricher(timeSeriesDataProvider)
            val meter1 = meter("rps", scenario = "sc-1")
            val meter2 = meter("cpu", scenario = "sc-2")
            coEvery {
                timeSeriesDataProvider.retrieveCampaignMeters("my-tenant", listOf("camp-1"), listOf("sc-1", "sc-2"))
            } returns listOf(meter1, meter2)

            // when
            val distribution = subject.distribute("my-tenant", listOf("camp-1"), listOf("sc-1", "sc-2"))

            // then
            assertThat(distribution["camp-1"]!!.scenarioMeters("sc-1")).containsExactly(meter1)
            assertThat(distribution["camp-1"]!!.scenarioMeters("sc-2")).containsExactly(meter2)
            coVerify {
                timeSeriesDataProvider.retrieveCampaignMeters(
                    "my-tenant",
                    listOf("camp-1"),
                    listOf("sc-1", "sc-2")
                )
            }
            confirmVerified(timeSeriesDataProvider)
        }

    @Test
    internal fun `stepMeters should return meters for the requested scenario and step`() =
        testDispatcherProvider.runTest {
            // given
            val subject = CampaignMeterEnricher(timeSeriesDataProvider)
            val meterA = meter("latency", scenario = "sc-1", stepTag = "step-A")
            val meterB = meter("errors", scenario = "sc-1", stepTag = "step-B")
            coEvery {
                timeSeriesDataProvider.retrieveCampaignMeters("my-tenant", listOf("camp-1"), listOf("sc-1"))
            } returns listOf(meterA, meterB)

            // when
            val distribution = subject.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))

            // then
            assertThat(distribution["camp-1"]!!.stepMeters("sc-1", "step-A")).containsExactly(meterA)
            assertThat(distribution["camp-1"]!!.stepMeters("sc-1", "step-B")).containsExactly(meterB)
            coVerify { timeSeriesDataProvider.retrieveCampaignMeters("my-tenant", listOf("camp-1"), listOf("sc-1")) }
            confirmVerified(timeSeriesDataProvider)
        }

    @Test
    internal fun `scenarioMeters should return empty list for unknown scenario`() = testDispatcherProvider.runTest {
        // given
        val subject = CampaignMeterEnricher(timeSeriesDataProvider)
        coEvery {
            timeSeriesDataProvider.retrieveCampaignMeters("my-tenant", listOf("camp-1"), listOf("sc-1"))
        } returns emptyList()

        // when
        val distribution = subject.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))

        // then
        assertThat(distribution["camp-1"]!!.scenarioMeters("unknown-scenario")).isEmpty()
        coVerify { timeSeriesDataProvider.retrieveCampaignMeters("my-tenant", listOf("camp-1"), listOf("sc-1")) }
        confirmVerified(timeSeriesDataProvider)
    }

    @Test
    internal fun `stepMeters should return empty list for unknown step`() = testDispatcherProvider.runTest {
        // given
        val subject = CampaignMeterEnricher(timeSeriesDataProvider)
        val meterA = meter("latency", scenario = "sc-1", stepTag = "step-A")
        coEvery {
            timeSeriesDataProvider.retrieveCampaignMeters("my-tenant", listOf("camp-1"), listOf("sc-1"))
        } returns listOf(meterA)

        // when
        val distribution = subject.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))

        // then
        assertThat(distribution["camp-1"]!!.stepMeters("sc-1", "unknown-step")).isEmpty()
        assertThat(distribution["camp-1"]!!.stepMeters("unknown-scenario", "step-A")).isEmpty()
        coVerify { timeSeriesDataProvider.retrieveCampaignMeters("my-tenant", listOf("camp-1"), listOf("sc-1")) }
        confirmVerified(timeSeriesDataProvider)
    }

    @Test
    internal fun `MeterDistribution data class getters should work correctly`() = testDispatcherProvider.runTest {
        // given
        val meter1 = meter("m1", scenario = null)
        val meter2 = meter("m2", scenario = "sc-1")
        val meter3 = meter("m3", scenario = "sc-1", stepTag = "step-X")

        val distribution = MeterDistribution(
            campaignMeters = listOf(meter1),
            byScenario = mapOf("sc-1" to listOf(meter2)),
            byScenarioAndStep = mapOf("sc-1" to mapOf("step-X" to listOf(meter3)))
        )

        // then
        assertThat(distribution.campaignMeters).hasSize(1)
        assertThat(distribution.scenarioMeters("sc-1")).containsExactly(meter2)
        assertThat(distribution.stepMeters("sc-1", "step-X")).containsExactly(meter3)
        assertThat(distribution.scenarioMeters("other")).isEmpty()
        assertThat(distribution.stepMeters("sc-1", "other-step")).isEmpty()
        assertThat(distribution.stepMeters("other", "step-X")).isEmpty()
    }
}
