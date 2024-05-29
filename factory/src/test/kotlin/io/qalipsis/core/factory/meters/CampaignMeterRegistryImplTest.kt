/*
 * QALIPSIS
 * Copyright (C) 2024 AERIS IT Solutions GmbH
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
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.MeasurementPublisherFactory
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Statistic
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.meters.catadioptre.currentCampaignKey
import io.qalipsis.core.factory.meters.catadioptre.meters
import io.qalipsis.core.factory.meters.catadioptre.publishers
import io.qalipsis.core.factory.meters.catadioptre.publishingTimer
import io.qalipsis.core.factory.meters.catadioptre.takeSnapshots
import io.qalipsis.core.reporter.MeterReporter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyExactly
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class CampaignMeterRegistryImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var meterReporter: MeterReporter

    @RelaxedMockK
    lateinit var factoryConfiguration: FactoryConfiguration

    @RelaxedMockK
    lateinit var measurementConfiguration: MeasurementConfiguration

    @RelaxedMockK
    lateinit var publisherFactory1: MeasurementPublisherFactory

    @RelaxedMockK
    lateinit var publisherFactory2: MeasurementPublisherFactory

    @RelaxedMockK
    lateinit var campaign: Campaign

    val coroutineScope = relaxedMockk<CoroutineScope>()

    @Test
    internal fun `should successfully initialize the meter registry`() = testDispatcherProvider.run {
        //given
        val meterRegistry = spyk(
            CampaignMeterRegistryImpl(
                listOf(publisherFactory1, publisherFactory2),
                factoryConfiguration,
                meterReporter,
                Duration.ofSeconds(10),
                measurementConfiguration,
                coroutineScope
            ), recordPrivateCalls = true
        )

        //when
        meterRegistry.init(campaign)

        //then
        assertThat(meterRegistry.meters().isEmpty())
        assertThat(meterRegistry.currentCampaignKey()).isEqualTo(campaign.campaignKey)
        coVerifyOnce {
            meterRegistry.init(campaign)
            publisherFactory1.getPublisher()
            publisherFactory2.getPublisher()
            meterRegistry.startPublishingJob()
            meterRegistry.publishers()[0].init()
            meterRegistry.publishers()[1].init()
        }
        confirmVerified(publisherFactory1, meterRegistry)
    }

    @Test
    internal fun `should successfully close the meter registry when close is called`() = testDispatcherProvider.run {
        //given
        val meterRegistry = spyk(
            CampaignMeterRegistryImpl(
                listOf(publisherFactory1, publisherFactory2),
                factoryConfiguration,
                meterReporter,
                Duration.ofSeconds(10),
                measurementConfiguration,
                coroutineScope
            ), recordPrivateCalls = true
        )
        meterRegistry.init(campaign)
        Assertions.assertNotNull(meterRegistry.publishingTimer())

        //when
        meterRegistry.close(campaign)

        //then
        assertThat(meterRegistry.meters().isEmpty())
        coVerifyOnce {
            meterRegistry.init(campaign)
            meterRegistry.startPublishingJob()
            meterRegistry.close(campaign)
            meterRegistry.clear()
            meterRegistry.meters().clear()
            meterRegistry.publishingTimer()?.cancel()
            meterRegistry.publishers()[0].stop()
            meterRegistry.publishers()[1].stop()
        }
        confirmVerified(meterRegistry)
    }

    @Test
    internal fun `should successfully take snapshots of each meters`() = testDispatcherProvider.run {
        //given
        val meterRegistry = spyk(
            CampaignMeterRegistryImpl(
                listOf(publisherFactory1, publisherFactory2),
                factoryConfiguration,
                meterReporter,
                Duration.ofSeconds(10),
                measurementConfiguration,
                coroutineScope
            ), recordPrivateCalls = true
        )
        val now = Instant.now()
        val counterId = Meter.Id(
            campaignKey = "my-campaign",
            scenarioName = "my-scenario",
            stepName = "my-step",
            tags = mapOf("foo" to "bar"),
            type = MeterType.COUNTER,
            meterName = "my-counter"
        )
        val gaugeId = Meter.Id(
            campaignKey = "my-campaign",
            scenarioName = "my-scenario",
            stepName = "my-step",
            tags = mapOf("foo" to "bar"),
            type = MeterType.GAUGE,
            meterName = "my-gauge"
        )
        val summaryId = Meter.Id(
            campaignKey = "my-campaign",
            scenarioName = "my-scenario",
            stepName = "my-step",
            tags = mapOf("foo" to "bar"),
            type = MeterType.DISTRIBUTION_SUMMARY,
            meterName = "my-summary"
        )
        val timerId = Meter.Id(
            campaignKey = "my-campaign",
            scenarioName = "my-scenario",
            stepName = "my-step",
            tags = mapOf("foo" to "bar"),
            type = MeterType.TIMER,
            meterName = "my-timer"
        )
        val counter = spyk<Counter>(CounterImpl(counterId, meterReporter))
        counter.increment(5.0)

        val gauge = GaugeImpl(gaugeId, meterReporter)
        gauge.increment(5.0)
        gauge.decrement(2.0)

        val timer = TimerImpl(timerId, meterReporter, listOf())
        timer.record(Duration.ofSeconds(10))
        timer.record(Duration.ofSeconds(5))
        timer.record(Duration.ofSeconds(3))

        val summary = DistributionSummaryImpl(summaryId, meterReporter, listOf())
        summary.record(12893.0)
        summary.record(56839.0)
        summary.record(111111.0)
        val meters = mapOf(
            counterId to counter,
            gaugeId to gauge,
            timerId to timer,
            summaryId to summary
        )

        //when
        val snapshots =
            meterRegistry.coInvokeInvisible<Collection<MeterSnapshot<*>>>("takeSnapshots", now, meters) as List

        //then
        assertThat(snapshots.size).isEqualTo(4)
        assertThat(snapshots[0]).prop(MeterSnapshot<*>::timestamp).isEqualTo(now)
        assertThat(snapshots[0]).prop(MeterSnapshot<*>::meter).isEqualTo(counter)
        val countMeasurement = snapshots[0].measurements as List
        assertThat(countMeasurement[0].value).isEqualTo(5.0)
        assertThat(countMeasurement[0].statistic).isEqualTo(Statistic.COUNT)
        assertThat(snapshots[1]).prop(MeterSnapshot<*>::timestamp).isEqualTo(now)
        assertThat(snapshots[1]).prop(MeterSnapshot<*>::meter).isEqualTo(gauge)
        val gaugeMeasurement = snapshots[1].measurements as List
        assertThat(gaugeMeasurement[0].value).isEqualTo(3.0)
        assertThat(gaugeMeasurement[0].statistic).isEqualTo(Statistic.VALUE)
        assertThat(snapshots[2]).prop(MeterSnapshot<*>::timestamp).isEqualTo(now)
        assertThat(snapshots[2]).prop(MeterSnapshot<*>::meter).isEqualTo(timer)
        val timerMeasurement = snapshots[2].measurements as List
        assertThat(timerMeasurement[0].value).isEqualTo(3.0)
        assertThat(timerMeasurement[0].statistic).isEqualTo(Statistic.COUNT)
        assertThat(BigDecimal(timerMeasurement[1].value)).isEqualTo(BigDecimal(18000000))
        assertThat(timerMeasurement[1].statistic).isEqualTo(Statistic.TOTAL_TIME)
        assertThat(BigDecimal(timerMeasurement[2].value)).isEqualTo(BigDecimal(10000000))
        assertThat(timerMeasurement[2].statistic).isEqualTo(Statistic.MAX)
        assertThat(BigDecimal(timerMeasurement[3].value)).isEqualTo(BigDecimal(6000000))
        assertThat(timerMeasurement[3].statistic).isEqualTo(Statistic.MEAN)

        assertThat(snapshots[3]).prop(MeterSnapshot<*>::timestamp).isEqualTo(now)
        assertThat(snapshots[3]).prop(MeterSnapshot<*>::meter).isEqualTo(summary)
        val summaryMeasurement = snapshots[3].measurements as List
        assertThat(summaryMeasurement[0].value).isEqualTo(3.0)
        assertThat(summaryMeasurement[0].statistic).isEqualTo(Statistic.COUNT)
        assertThat(summaryMeasurement[1].value).isEqualTo(180843.0)
        assertThat(summaryMeasurement[1].statistic).isEqualTo(Statistic.TOTAL)
        assertThat(BigDecimal(summaryMeasurement[2].value)).isEqualTo(BigDecimal(111111))
        assertThat(summaryMeasurement[2].statistic).isEqualTo(Statistic.MAX)
        assertThat(summaryMeasurement[3].value).isEqualTo(60281.0)
        assertThat(summaryMeasurement[3].statistic).isEqualTo(Statistic.MEAN)
        coVerifyOnce {
            counter.buildSnapshot(now)
            summary.buildSnapshot(now)
            gauge.buildSnapshot(now)
            timer.buildSnapshot(now)
            counter.measure()
            summary.measure()
            gauge.measure()
            timer.measure()
        }
    }

    @Test
    internal fun `should handle the publishing job when no job is in progress`() = testDispatcherProvider.run {
        //given
        val meterRegistry = spyk(
            CampaignMeterRegistryImpl(
                listOf(publisherFactory1, publisherFactory2),
                factoryConfiguration,
                meterReporter,
                Duration.ofSeconds(1),
                measurementConfiguration,
                coroutineScope = this
            ), recordPrivateCalls = true
        )
        val now = Instant.now()
        val snapshots = emptyList<MeterSnapshot<*>>()
        coEvery {
            meterRegistry.coInvokeInvisible<List<MeterSnapshot<*>>>(
                "takeSnapshots",
                any<Instant>(),
                any<Map<Meter.Id, Meter<*>>>()
            )
        } returns snapshots

        //when
        meterRegistry.init(campaign)
        delay(2000)

        //then
        coVerifyOnce {
            meterRegistry.init(campaign)
            publisherFactory1.getPublisher()
            publisherFactory2.getPublisher()
            meterRegistry.startPublishingJob()
            meterRegistry.publishers()[0].init()
            meterRegistry.publishers()[1].init()
        }

        coVerifyExactly(2) {
            meterRegistry.takeSnapshots(any<Instant>(), emptyMap())
            meterRegistry.publishers()[0].publish(snapshots)
            meterRegistry.publishers()[1].publish(snapshots)
        }
        confirmVerified(publisherFactory1, meterRegistry)
    }

    @Test
    internal fun `should create a counter when given the right args`() = testDispatcherProvider.run {
        // given
        val meterRegistry = spyk(
            CampaignMeterRegistryImpl(
                listOf(publisherFactory1, publisherFactory2),
                factoryConfiguration,
                meterReporter,
                Duration.ofSeconds(10),
                measurementConfiguration,
                coroutineScope
            ), recordPrivateCalls = true
        )
        meterRegistry.init(campaign)

        // when
        meterRegistry.counter(
            scenarioName = "my-scenario",
            stepName = "my-step",
            name = "my-counter",
            tags = mapOf("foo" to "bar")
        )

        // then
        assertThat(meterRegistry.meters().size).isEqualTo(1)
        assertThat(meterRegistry.meters().keys.first()).isEqualTo(
            Meter.Id(
                campaignKey = campaign.campaignKey,
                scenarioName = "my-scenario",
                stepName = "my-step",
                tags = mapOf("foo" to "bar"),
                type = MeterType.COUNTER,
                meterName = "my-counter"
            )
        )
        meterRegistry.close(campaign)
    }

    @Test
    internal fun `should create a counter meter from var args`() = testDispatcherProvider.run {
        // given
        val meterRegistry = spyk(
            CampaignMeterRegistryImpl(
                listOf(publisherFactory1, publisherFactory2),
                factoryConfiguration,
                meterReporter,
                Duration.ofSeconds(10),
                measurementConfiguration,
                coroutineScope
            ), recordPrivateCalls = true
        )
        meterRegistry.init(campaign)

        // when
        meterRegistry.counter(
            name = "my-counter",
            tags = arrayOf("scenario", "my-scenario", "step", "my-step", "foo", "bar")
        )

        // then
        assertThat(meterRegistry.meters().size).isEqualTo(1)
        assertThat(meterRegistry.meters().keys.first()).isEqualTo(
            Meter.Id(
                campaignKey = campaign.campaignKey,
                scenarioName = "my-scenario",
                stepName = "my-step",
                tags = mapOf("scenario" to "my-scenario", "step" to "my-step", "foo" to "bar"),
                type = MeterType.COUNTER,
                meterName = "my-counter"
            )
        )
        meterRegistry.close(campaign)
    }

    @Test
    internal fun `should create a gauge when given the right args`() = testDispatcherProvider.run {
        // given
        val meterRegistry = spyk(
            CampaignMeterRegistryImpl(
                listOf(publisherFactory1, publisherFactory2),
                factoryConfiguration,
                meterReporter,
                Duration.ofSeconds(10),
                measurementConfiguration,
                coroutineScope
            ), recordPrivateCalls = true
        )
        meterRegistry.init(campaign)

        // when
        meterRegistry.gauge(
            scenarioName = "my-scenario",
            stepName = "my-step",
            name = "my-gauge",
            tags = mapOf("foo" to "bar")
        )

        // then
        assertThat(meterRegistry.meters().size).isEqualTo(1)
        assertThat(meterRegistry.meters().keys.first()).isEqualTo(
            Meter.Id(
                campaignKey = campaign.campaignKey,
                scenarioName = "my-scenario",
                stepName = "my-step",
                tags = mapOf("foo" to "bar"),
                type = MeterType.GAUGE,
                meterName = "my-gauge"
            )
        )
        meterRegistry.close(campaign)
    }

    @Test
    internal fun `should create a gauge meter from var args`() = testDispatcherProvider.run {
        // given
        val meterRegistry = spyk(
            CampaignMeterRegistryImpl(
                listOf(publisherFactory1, publisherFactory2),
                factoryConfiguration,
                meterReporter,
                Duration.ofSeconds(10),
                measurementConfiguration,
                coroutineScope
            ), recordPrivateCalls = true
        )
        meterRegistry.init(campaign)

        // when
        meterRegistry.gauge(
            name = "my-gauge",
            tags = arrayOf("scenario", "my-scenario", "step", "my-step", "foo", "bar")
        )

        // then
        assertThat(meterRegistry.meters().size).isEqualTo(1)
        assertThat(meterRegistry.meters().keys.first()).isEqualTo(
            Meter.Id(
                campaignKey = campaign.campaignKey,
                scenarioName = "my-scenario",
                stepName = "my-step",
                tags = mapOf("scenario" to "my-scenario", "step" to "my-step", "foo" to "bar"),
                type = MeterType.GAUGE,
                meterName = "my-gauge"
            )
        )
        meterRegistry.close(campaign)
    }

    @Test
    internal fun `should create a timer when given the right args`() = testDispatcherProvider.run {
        // given
        val meterRegistry = spyk(
            CampaignMeterRegistryImpl(
                listOf(publisherFactory1, publisherFactory2),
                factoryConfiguration,
                meterReporter,
                Duration.ofSeconds(10),
                measurementConfiguration,
                coroutineScope
            ), recordPrivateCalls = true
        )
        meterRegistry.init(campaign)

        // when
        meterRegistry.timer(
            scenarioName = "my-scenario",
            stepName = "my-step",
            name = "my-timer",
            tags = mapOf("foo" to "bar"),
            listOf(99.0, 50.0)
        )

        // then
        assertThat(meterRegistry.meters().size).isEqualTo(1)
        assertThat(meterRegistry.meters().keys.first()).isEqualTo(
            Meter.Id(
                campaignKey = campaign.campaignKey,
                scenarioName = "my-scenario",
                stepName = "my-step",
                tags = mapOf("foo" to "bar"),
                type = MeterType.TIMER,
                meterName = "my-timer"
            )
        )
        meterRegistry.close(campaign)
    }

    @Test
    internal fun `should create a timer meter from var args`() = testDispatcherProvider.run {
        // given
        val meterRegistry = spyk(
            CampaignMeterRegistryImpl(
                listOf(publisherFactory1, publisherFactory2),
                factoryConfiguration,
                meterReporter,
                Duration.ofSeconds(10),
                measurementConfiguration,
                coroutineScope
            ), recordPrivateCalls = true
        )
        meterRegistry.init(campaign)

        // when
        meterRegistry.timer(
            name = "my-timer",
            tags = arrayOf("scenario", "my-scenario", "step", "my-step", "foo", "bar")
        )

        // then
        assertThat(meterRegistry.meters().size).isEqualTo(1)
        assertThat(meterRegistry.meters().keys.first()).isEqualTo(
            Meter.Id(
                campaignKey = campaign.campaignKey,
                scenarioName = "my-scenario",
                stepName = "my-step",
                tags = mapOf("scenario" to "my-scenario", "step" to "my-step", "foo" to "bar"),
                type = MeterType.TIMER,
                meterName = "my-timer"
            )
        )
        meterRegistry.close(campaign)
    }

    @Test
    internal fun `should create a summary when given the right args`() = testDispatcherProvider.run {
        // given
        val meterRegistry = spyk(
            CampaignMeterRegistryImpl(
                listOf(publisherFactory1, publisherFactory2),
                factoryConfiguration,
                meterReporter,
                Duration.ofSeconds(10),
                measurementConfiguration,
                coroutineScope
            ), recordPrivateCalls = true
        )
        meterRegistry.init(campaign)

        // when
        meterRegistry.summary(
            scenarioName = "my-scenario",
            stepName = "my-step",
            name = "my-summary",
            tags = mapOf("foo" to "bar"),
            listOf(99.0, 50.0)
        )

        // then
        assertThat(meterRegistry.meters().size).isEqualTo(1)
        assertThat(meterRegistry.meters().keys.first()).isEqualTo(
            Meter.Id(
                campaignKey = campaign.campaignKey,
                scenarioName = "my-scenario",
                stepName = "my-step",
                tags = mapOf("foo" to "bar"),
                type = MeterType.DISTRIBUTION_SUMMARY,
                meterName = "my-summary"
            )
        )
        meterRegistry.close(campaign)
    }

    @Test
    internal fun `should create a summary meter from var args`() = testDispatcherProvider.run {
        // given
        val meterRegistry = spyk(
            CampaignMeterRegistryImpl(
                listOf(publisherFactory1, publisherFactory2),
                factoryConfiguration,
                meterReporter,
                Duration.ofSeconds(10),
                measurementConfiguration,
                coroutineScope
            ), recordPrivateCalls = true
        )
        meterRegistry.init(campaign)

        // when
        meterRegistry.summary(
            name = "my-summary",
            tags = arrayOf("scenario", "my-scenario", "step", "my-step", "foo", "bar")
        )

        // then
        assertThat(meterRegistry.meters().size).isEqualTo(1)
        assertThat(meterRegistry.meters().keys.first()).isEqualTo(
            Meter.Id(
                campaignKey = campaign.campaignKey,
                scenarioName = "my-scenario",
                stepName = "my-step",
                tags = mapOf("scenario" to "my-scenario", "step" to "my-step", "foo" to "bar"),
                type = MeterType.DISTRIBUTION_SUMMARY,
                meterName = "my-summary"
            )
        )
        meterRegistry.close(campaign)
    }

}