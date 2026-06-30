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

package io.qalipsis.core.factory.meters

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Statistic
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.feedbacks.CampaignMetersFeedback
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

@WithMockk
internal class CampaignMeterChannelPublisherTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var factoryChannel: FactoryChannel

    private val objectMapper = ObjectMapper()

    private lateinit var publisher: CampaignMeterChannelPublisher

    @BeforeEach
    fun setUp() {
        publisher = CampaignMeterChannelPublisher(factoryChannel, objectMapper)
    }

    @Test
    internal fun `getPublisher returns this`() {
        assertThat(publisher.getPublisher()).isSameInstanceAs(publisher)
    }

    @Test
    internal fun `given empty meters when publish then nothing sent`() = testDispatcherProvider.run {
        // when
        publisher.publish(emptyList())

        // then
        coVerifyNever { factoryChannel.publishMeterFeedback(any()) }
    }

    @Test
    internal fun `given only non-campaign-scoped meters when publish then nothing sent`() = testDispatcherProvider.run {
        // given
        val globalSnapshot = buildSnapshot(MeterType.COUNTER, mapOf("scope" to "global"))
        val stepSnapshot = buildSnapshot(MeterType.GAUGE, mapOf("scope" to "step"))

        // when
        publisher.publish(listOf(globalSnapshot, stepSnapshot))

        // then
        coVerifyNever { factoryChannel.publishMeterFeedback(any()) }
    }

    @Test
    internal fun `given campaign-scoped meters when publish then feedback published with converted snapshots`() =
        testDispatcherProvider.run {
            // given
            val campaignSnapshot = buildSnapshot(
                MeterType.COUNTER,
                mapOf(
                    "scope" to "campaign",
                    "tenant" to "tenant-1",
                    "campaign" to "campaign-1",
                    "scenario" to "scenario-1"
                ),
                measurements = listOf(buildMeasurement(Statistic.COUNT, 10.0))
            )
            val slot = slot<CampaignMetersFeedback>()
            coJustRun { factoryChannel.publishMeterFeedback(capture(slot)) }

            // when
            publisher.publish(listOf(campaignSnapshot))

            // then
            coVerify(exactly = 1) { factoryChannel.publishMeterFeedback(any()) }
            assertThat(slot.captured.meters).hasSize(1)
            assertThat(slot.captured.meters[0].name).isEqualTo("test-meter")
            assertThat(slot.captured.meters[0].tenant).isEqualTo("tenant-1")
            assertThat(slot.captured.meters[0].campaign).isEqualTo("campaign-1")
            assertThat(slot.captured.meters[0].scenario).isEqualTo("scenario-1")
            assertThat(slot.captured.meters[0].count).isEqualTo(10.0)
        }

    @Test
    internal fun `given meters with no scope tag when publish then nothing sent`() = testDispatcherProvider.run {
        // given
        val noScopeSnapshot = buildSnapshot(MeterType.COUNTER, mapOf("tenant" to "t1"))

        // when
        publisher.publish(listOf(noScopeSnapshot))

        // then
        coVerifyNever { factoryChannel.publishMeterFeedback(any()) }
    }

    @Test
    internal fun `given running-minions gauge with no scope when publish then feedback published`() =
        testDispatcherProvider.run {
            // given
            val runningMinionsSnapshot = buildSnapshot(
                MeterType.GAUGE,
                mapOf("campaign" to "campaign-1", "scenario" to "scenario-1", "step" to "step-1"),
                meterName = "running-minions",
                measurements = listOf(buildMeasurement(Statistic.VALUE, 42.0))
            )
            val slot = slot<CampaignMetersFeedback>()
            coJustRun { factoryChannel.publishMeterFeedback(capture(slot)) }

            // when
            publisher.publish(listOf(runningMinionsSnapshot))

            // then
            coVerify(exactly = 1) { factoryChannel.publishMeterFeedback(any()) }
            assertThat(slot.captured.meters).hasSize(1)
            assertThat(slot.captured.meters[0].name).isEqualTo("running-minions")
            assertThat(slot.captured.meters[0].campaign).isEqualTo("campaign-1")
        }

    @Test
    internal fun `given mix of campaign, running-minions and other meters when publish then campaign and running-minions sent`() =
        testDispatcherProvider.run {
            // given
            val campaignSnapshot1 = buildSnapshot(
                MeterType.COUNTER,
                mapOf("scope" to "campaign"),
                measurements = listOf(buildMeasurement(Statistic.COUNT, 5.0))
            )
            val campaignSnapshot2 = buildSnapshot(
                MeterType.GAUGE,
                mapOf("scope" to "campaign"),
                measurements = listOf(buildMeasurement(Statistic.VALUE, 3.14))
            )
            val runningMinionsSnapshot = buildSnapshot(
                MeterType.GAUGE,
                mapOf("campaign" to "campaign-1", "scenario" to "scenario-1", "step" to "step-1"),
                meterName = "running-minions",
                measurements = listOf(buildMeasurement(Statistic.VALUE, 7.0))
            )
            val nonCampaignSnapshot = buildSnapshot(MeterType.COUNTER, mapOf("scope" to "global"))
            val slot = slot<CampaignMetersFeedback>()
            coJustRun { factoryChannel.publishMeterFeedback(capture(slot)) }

            // when
            publisher.publish(listOf(campaignSnapshot1, nonCampaignSnapshot, runningMinionsSnapshot, campaignSnapshot2))

            // then
            coVerify(exactly = 1) { factoryChannel.publishMeterFeedback(any()) }
            assertThat(slot.captured.meters).hasSize(3)
        }

    private fun buildSnapshot(
        type: MeterType,
        tags: Map<String, String>,
        meterName: String = "test-meter",
        measurements: List<Measurement> = emptyList()
    ): MeterSnapshot {
        val meterId = Meter.Id(meterName = meterName, type = type, tags = tags)
        val snapshot = io.mockk.mockk<MeterSnapshot>()
        every { snapshot.meterId } returns meterId
        every { snapshot.timestamp } returns Instant.ofEpochMilli(1_000_000L)
        every { snapshot.measurements } returns measurements
        return snapshot
    }

    private fun buildMeasurement(statistic: Statistic, value: Double): Measurement {
        val m = io.mockk.mockk<Measurement>()
        every { m.statistic } returns statistic
        every { m.value } returns value
        return m
    }
}
