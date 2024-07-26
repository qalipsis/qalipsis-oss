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
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.mockk.coEvery
import io.mockk.coExcludeRecords
import io.mockk.coJustRun
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.MeasurementPublisher
import io.qalipsis.api.meters.MeasurementPublisherFactory
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Rate
import io.qalipsis.api.meters.Throughput
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.meters.catadioptre.additionalTags
import io.qalipsis.core.factory.meters.catadioptre.currentCampaignKey
import io.qalipsis.core.factory.meters.catadioptre.publishSnapshots
import io.qalipsis.core.factory.meters.catadioptre.startPublishingJob
import io.qalipsis.core.factory.meters.catadioptre.ticker
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.verifyOnce
import java.time.Duration
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@WithMockk
internal class CampaignMeterRegistryFacadeImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    lateinit var meterRegistry: MeterRegistry

    @MockK
    lateinit var factoryConfiguration: FactoryConfiguration

    @MockK
    lateinit var measurementConfiguration: MeasurementConfiguration

    @MockK
    lateinit var publisherFactory1: MeasurementPublisherFactory

    @MockK
    lateinit var publisherFactory2: MeasurementPublisherFactory

    @MockK
    lateinit var campaign: Campaign


    @ParameterizedTest
    @ValueSource(longs = [60, 100, 200])
    internal fun `should successfully initialize the meter registry and publish measurements`(publicationPeriodMs: Long) =
        testDispatcherProvider.run {
            //given
            every { factoryConfiguration.tags } returns mapOf("tag-1" to "value-1", "tag-2" to "value-2")
            every { factoryConfiguration.zone } returns "at"
            // Stores the timestamp in ms, when the publication is triggered, in order to verify the period.
            val publicationTimestamps = mutableListOf<Long>()
            // Latch to wait until all publications are completed.
            val latch = SuspendedCountLatch(4)

            val campaignMeterRegistry = spyk(
                CampaignMeterRegistryFacadeImpl(
                    listOf(publisherFactory1, publisherFactory2),
                    meterRegistry,
                    factoryConfiguration,
                    measurementConfiguration,
                    Duration.ofMillis(publicationPeriodMs),
                    this
                ), recordPrivateCalls = true
            ) {
                every { this@spyk["publishSnapshots"](any<Collection<MeterSnapshot>>()) } coAnswers {
                    publicationTimestamps += System.currentTimeMillis()
                    latch.decrement()
                    mockk<Collection<Job>>()
                }
            }
            val publisher1 = mockk<MeasurementPublisher> {
                coJustRun { init() }
            }
            every { publisherFactory1.getPublisher() } returns publisher1
            val publisher2 = mockk<MeasurementPublisher> {
                coJustRun { init() }
            }
            every { publisherFactory2.getPublisher() } returns publisher2
            val snapshots1 = mockk<Collection<MeterSnapshot>>("snapshots-1") { every { isEmpty() } returns false }
            val snapshots2 = mockk<Collection<MeterSnapshot>>("snapshots-2") { every { isEmpty() } returns false }
            coEvery { meterRegistry.snapshots(any()) } returnsMany listOf(
                snapshots1,
                emptyList(),
                snapshots2,
                emptyList()
            )
            val campaignKey = RandomStringUtils.randomAlphabetic(8)
            every { campaign.campaignKey } returns campaignKey

            //when
            campaignMeterRegistry.init(campaign)
            latch.await()

            //then
            assertThat(campaignMeterRegistry.ticker().isClosedForReceive).isFalse()
            assertThat(campaignMeterRegistry.currentCampaignKey()).isEqualTo(campaignKey)
            assertThat(campaignMeterRegistry.additionalTags()).isEqualTo(
                mapOf(
                    "tag-1" to "value-1",
                    "tag-2" to "value-2",
                    "zone" to "at"
                )
            )
            // Verifies that the publication occurs on a regular basis.
            publicationTimestamps.windowed(2, 1).forEach { timestamps ->
                assertThat(timestamps[1] - timestamps[0]).isBetween(publicationPeriodMs - 10, publicationPeriodMs + 10)
            }
            coExcludeRecords {
                campaignMeterRegistry.init(any())
            }
            coVerifyOrder {
                publisherFactory1.getPublisher()
                publisherFactory2.getPublisher()
                publisher1.init()
                publisher2.init()
                campaignMeterRegistry.startPublishingJob()
                meterRegistry.snapshots(any())
                campaignMeterRegistry.publishSnapshots(refEq(snapshots1))
                meterRegistry.snapshots(any())
                campaignMeterRegistry.publishSnapshots(emptyList())
                meterRegistry.snapshots(any())
                campaignMeterRegistry.publishSnapshots(refEq(snapshots2))
                meterRegistry.snapshots(any())
                campaignMeterRegistry.publishSnapshots(emptyList())
            }
            coExcludeRecords {
                meterRegistry.snapshots(any())
                campaignMeterRegistry.publishSnapshots(emptyList())
            }

            confirmVerified(
                publisherFactory1,
                publisherFactory2,
                publisher1,
                publisher2,
                campaignMeterRegistry,
                meterRegistry
            )

        }

    @Test
    internal fun `should successfully close the meter registry and publish the total snapshots when close is called`() =
        testDispatcherProvider.run {
            //given
            every { factoryConfiguration.tags } returns mapOf("tag-1" to "value-1", "tag-2" to "value-2")
            every { factoryConfiguration.zone } returns "at"
            val job1 = mockk<Job> { coEvery { join() } coAnswers { delay(100) } }
            val job2 = mockk<Job> { coJustRun { join() } }
            val campaignMeterRegistry = spyk(
                CampaignMeterRegistryFacadeImpl(
                    listOf(publisherFactory1, publisherFactory2),
                    meterRegistry,
                    factoryConfiguration,
                    measurementConfiguration,
                    Duration.ofSeconds(10),
                    this
                ), recordPrivateCalls = true
            ) {
                every { this@spyk["publishSnapshots"](any<Collection<MeterSnapshot>>()) } returns listOf(job1, job2)
            }

            val snapshots = mockk<Collection<MeterSnapshot>>("snapshots") { every { isEmpty() } returns false }
            coEvery { meterRegistry.summarize(any()) } returns snapshots

            val publisher1 = mockk<MeasurementPublisher> {
                coJustRun { init() }
                coJustRun { stop() }
            }
            every { publisherFactory1.getPublisher() } returns publisher1
            val publisher2 = mockk<MeasurementPublisher> {
                coJustRun { init() }
                coJustRun { stop() }
            }
            every { publisherFactory2.getPublisher() } returns publisher2
            every { campaign.campaignKey } returns RandomStringUtils.randomAlphanumeric(5)
            campaignMeterRegistry.init(campaign)
            assertThat(campaignMeterRegistry.ticker().isClosedForReceive).isFalse()

            //when
            campaignMeterRegistry.close(campaign)

            //then
            assertThat(campaignMeterRegistry.ticker().isClosedForReceive).isTrue()
            coVerifyOrder {
                campaignMeterRegistry.init(campaign)
                publisherFactory1.getPublisher()
                publisherFactory2.getPublisher()
                publisher1.init()
                publisher2.init()
                campaignMeterRegistry.startPublishingJob()

                campaignMeterRegistry.close(campaign)
                meterRegistry.summarize(any())
                campaignMeterRegistry.publishSnapshots(refEq(snapshots))
                job1.join()
                job2.join()
                publisher1.stop()
                publisher2.stop()
            }
            confirmVerified(
                publisherFactory1,
                publisherFactory2,
                publisher1,
                publisher2,
                campaignMeterRegistry,
                meterRegistry
            )
        }

    @Test
    fun `should publish the snapshots when not empty`() = testDispatcherProvider.run {
        // given
        every { factoryConfiguration.zone } returns null
        every { factoryConfiguration.tags } returns emptyMap()
        val campaignMeterRegistry = CampaignMeterRegistryFacadeImpl(
            publisherFactories = listOf(publisherFactory1, publisherFactory2),
            meterRegistry = meterRegistry,
            factoryConfiguration = factoryConfiguration,
            measurementConfiguration = measurementConfiguration,
            step = Duration.ofSeconds(10),
            coroutineScope = this
        )
        val latch = SuspendedCountLatch(2)
        val publisher1 = mockk<MeasurementPublisher> {
            coJustRun { init() }
            coEvery { publish(any()) } coAnswers { latch.decrement() }
        }
        every { publisherFactory1.getPublisher() } returns publisher1
        val publisher2 = mockk<MeasurementPublisher> {
            coJustRun { init() }
            coEvery { publish(any()) } coAnswers { latch.decrement() }
        }
        every { publisherFactory2.getPublisher() } returns publisher2
        every { campaign.campaignKey } returns RandomStringUtils.randomAlphanumeric(5)
        campaignMeterRegistry.init(campaign)
        val snapshots = mockk<Collection<MeterSnapshot>>("snapshots") { every { isEmpty() } returns false }

        // when
        campaignMeterRegistry.coInvokeInvisible<Collection<Job>>("publishSnapshots", snapshots)
        latch.await()

        // then
        coExcludeRecords {
            publisher1.init()
            publisher2.init()
        }
        coVerifyOnce {
            publisher1.publish(refEq(snapshots))
            publisher2.publish(refEq(snapshots))
        }
        confirmVerified(publisher1, publisher2)
    }

    @Test
    fun `should publish the snapshots even when one publisher fails`() = testDispatcherProvider.run {
        // given
        every { factoryConfiguration.zone } returns null
        every { factoryConfiguration.tags } returns emptyMap()
        val campaignMeterRegistry = CampaignMeterRegistryFacadeImpl(
            publisherFactories = listOf(publisherFactory1, publisherFactory2),
            meterRegistry = meterRegistry,
            factoryConfiguration = factoryConfiguration,
            measurementConfiguration = measurementConfiguration,
            step = Duration.ofSeconds(10),
            coroutineScope = this
        )
        val latch = SuspendedCountLatch(2)
        val publisher1 = mockk<MeasurementPublisher> {
            coJustRun { init() }
            coEvery { publish(any()) } coAnswers { latch.decrement(); throw RuntimeException() }
        }
        every { publisherFactory1.getPublisher() } returns publisher1
        val publisher2 = mockk<MeasurementPublisher> {
            coJustRun { init() }
            coEvery { publish(any()) } coAnswers { latch.decrement() }
        }
        every { publisherFactory2.getPublisher() } returns publisher2
        every { campaign.campaignKey } returns RandomStringUtils.randomAlphanumeric(5)
        campaignMeterRegistry.init(campaign)
        val snapshots = mockk<Collection<MeterSnapshot>>("snapshots") { every { isEmpty() } returns false }

        // when
        campaignMeterRegistry.coInvokeInvisible<Collection<Job>>("publishSnapshots", snapshots)
        latch.await()

        // then
        coExcludeRecords {
            publisher1.init()
            publisher2.init()
        }
        coVerifyOnce {
            publisher1.publish(refEq(snapshots))
            publisher2.publish(refEq(snapshots))
        }
        confirmVerified(publisher1, publisher2)
    }

    @Test
    fun `should not publish the empty snapshots`() = testDispatcherProvider.run {
        // given
        every { factoryConfiguration.zone } returns null
        every { factoryConfiguration.tags } returns emptyMap()
        val campaignMeterRegistry = CampaignMeterRegistryFacadeImpl(
            publisherFactories = listOf(publisherFactory1, publisherFactory2),
            meterRegistry = meterRegistry,
            factoryConfiguration = factoryConfiguration,
            measurementConfiguration = measurementConfiguration,
            step = Duration.ofSeconds(10),
            coroutineScope = this
        )
        val publisher1 = mockk<MeasurementPublisher> {
            coJustRun { init() }
        }
        every { publisherFactory1.getPublisher() } returns publisher1
        val publisher2 = mockk<MeasurementPublisher> {
            coJustRun { init() }
        }
        every { publisherFactory2.getPublisher() } returns publisher2
        every { campaign.campaignKey } returns RandomStringUtils.randomAlphanumeric(5)
        campaignMeterRegistry.init(campaign)

        // when
        campaignMeterRegistry.coInvokeInvisible<Collection<Job>>("publishSnapshots", emptyList<MeterSnapshot>())
        delay(60)

        // then
        coExcludeRecords {
            publisher1.init()
            publisher2.init()
        }
        confirmVerified(publisher1, publisher2)
    }

    @Test
    internal fun `should create a counter`() = testDispatcherProvider.run {
        // given
        every { factoryConfiguration.tags } returns mapOf("tag-1" to "value-1", "tag-2" to "value-2")
        every { factoryConfiguration.zone } returns "at"
        val campaignMeterRegistry = CampaignMeterRegistryFacadeImpl(
            publisherFactories = emptyList(),
            meterRegistry = meterRegistry,
            factoryConfiguration = factoryConfiguration,
            measurementConfiguration = measurementConfiguration,
            step = Duration.ofSeconds(10),
            coroutineScope = this
        )
        val campaignKey = RandomStringUtils.randomAlphabetic(8)
        val scenario = RandomStringUtils.randomAlphabetic(8)
        val step = RandomStringUtils.randomAlphabetic(8)
        val meterName = RandomStringUtils.randomAlphabetic(8)
        val tag1Key = RandomStringUtils.randomAlphabetic(8)
        val tag1Value = RandomStringUtils.randomAlphabetic(8)
        val tag2Key = RandomStringUtils.randomAlphabetic(8)
        val tag2Value = RandomStringUtils.randomAlphabetic(8)
        every { campaign.campaignKey } returns campaignKey
        campaignMeterRegistry.init(campaign)
        val counter = mockk<Counter>()
        every { meterRegistry.counter(any()) } returns counter

        // when
        val result = campaignMeterRegistry.counter(
            scenarioName = scenario,
            stepName = step,
            name = meterName,
            tags = mapOf(tag1Key to tag1Value, tag2Key to tag2Value)
        )

        // then
        assertThat(result).isSameAs(counter)
        verifyOnce {
            meterRegistry.counter(
                Meter.Id(
                    tags = mapOf(
                        tag1Key to tag1Value,
                        tag2Key to tag2Value,
                        "tag-1" to "value-1",
                        "tag-2" to "value-2",
                        "campaign" to campaignKey,
                        "scenario" to scenario,
                        "step" to step
                    ),
                    type = MeterType.COUNTER,
                    meterName = meterName
                )
            )
        }
        confirmVerified(meterRegistry, counter)
    }

    @Test
    internal fun `should create a counter with tags only`() = testDispatcherProvider.run {
        // given
        every { factoryConfiguration.tags } returns mapOf("tag-1" to "value-1", "tag-2" to "value-2")
        every { factoryConfiguration.zone } returns "at"
        val campaignMeterRegistry = CampaignMeterRegistryFacadeImpl(
            publisherFactories = emptyList(),
            meterRegistry = meterRegistry,
            factoryConfiguration = factoryConfiguration,
            measurementConfiguration = measurementConfiguration,
            step = Duration.ofSeconds(10),
            coroutineScope = this
        )
        val campaignKey = RandomStringUtils.randomAlphabetic(8)
        val scenario = RandomStringUtils.randomAlphabetic(8)
        val step = RandomStringUtils.randomAlphabetic(8)
        val meterName = RandomStringUtils.randomAlphabetic(8)
        val tag1Key = RandomStringUtils.randomAlphabetic(8)
        val tag1Value = RandomStringUtils.randomAlphabetic(8)
        val tag2Key = RandomStringUtils.randomAlphabetic(8)
        val tag2Value = RandomStringUtils.randomAlphabetic(8)
        every { campaign.campaignKey } returns campaignKey
        campaignMeterRegistry.init(campaign)
        val counter = mockk<Counter>()
        every { meterRegistry.counter(any()) } returns counter

        // when
        val result = campaignMeterRegistry.counter(
            meterName, "scenario", scenario, "step", step,
            tag1Key, tag1Value, tag2Key, tag2Value,
            "tag-1", "value-1", "tag-2", "value-2"
        )

        // then
        assertThat(result).isSameAs(counter)
        verifyOnce {
            meterRegistry.counter(
                Meter.Id(
                    tags = mapOf(
                        tag1Key to tag1Value,
                        tag2Key to tag2Value,
                        "tag-1" to "value-1",
                        "tag-2" to "value-2",
                        "campaign" to campaignKey,
                        "scenario" to scenario,
                        "step" to step
                    ),
                    type = MeterType.COUNTER,
                    meterName = meterName
                )
            )
        }
        confirmVerified(meterRegistry, counter)
    }

    @Test
    internal fun `should create a gauge`() = testDispatcherProvider.run {
        // given
        every { factoryConfiguration.tags } returns mapOf("tag-1" to "value-1", "tag-2" to "value-2")
        every { factoryConfiguration.zone } returns "at"
        val campaignMeterRegistry = CampaignMeterRegistryFacadeImpl(
            publisherFactories = emptyList(),
            meterRegistry = meterRegistry,
            factoryConfiguration = factoryConfiguration,
            measurementConfiguration = measurementConfiguration,
            step = Duration.ofSeconds(10),
            coroutineScope = this
        )
        val campaignKey = RandomStringUtils.randomAlphabetic(8)
        val scenario = RandomStringUtils.randomAlphabetic(8)
        val step = RandomStringUtils.randomAlphabetic(8)
        val meterName = RandomStringUtils.randomAlphabetic(8)
        val tag1Key = RandomStringUtils.randomAlphabetic(8)
        val tag1Value = RandomStringUtils.randomAlphabetic(8)
        val tag2Key = RandomStringUtils.randomAlphabetic(8)
        val tag2Value = RandomStringUtils.randomAlphabetic(8)
        every { campaign.campaignKey } returns campaignKey
        campaignMeterRegistry.init(campaign)
        val gauge = mockk<Gauge>()
        every { meterRegistry.gauge(any()) } returns gauge

        // when
        val result = campaignMeterRegistry.gauge(
            scenarioName = scenario,
            stepName = step,
            name = meterName,
            tags = mapOf(tag1Key to tag1Value, tag2Key to tag2Value)
        )

        // then
        assertThat(result).isSameAs(gauge)
        verifyOnce {
            meterRegistry.gauge(
                Meter.Id(
                    tags = mapOf(
                        tag1Key to tag1Value,
                        tag2Key to tag2Value,
                        "tag-1" to "value-1",
                        "tag-2" to "value-2",
                        "campaign" to campaignKey,
                        "scenario" to scenario,
                        "step" to step
                    ),
                    type = MeterType.GAUGE,
                    meterName = meterName
                )
            )
        }
        confirmVerified(meterRegistry, gauge)
    }

    @Test
    internal fun `should create a gauge with tags only`() = testDispatcherProvider.run {
        // given
        every { factoryConfiguration.tags } returns mapOf("tag-1" to "value-1", "tag-2" to "value-2")
        every { factoryConfiguration.zone } returns "at"
        val campaignMeterRegistry = CampaignMeterRegistryFacadeImpl(
            publisherFactories = emptyList(),
            meterRegistry = meterRegistry,
            factoryConfiguration = factoryConfiguration,
            measurementConfiguration = measurementConfiguration,
            step = Duration.ofSeconds(10),
            coroutineScope = this
        )
        val campaignKey = RandomStringUtils.randomAlphabetic(8)
        val scenario = RandomStringUtils.randomAlphabetic(8)
        val step = RandomStringUtils.randomAlphabetic(8)
        val meterName = RandomStringUtils.randomAlphabetic(8)
        val tag1Key = RandomStringUtils.randomAlphabetic(8)
        val tag1Value = RandomStringUtils.randomAlphabetic(8)
        val tag2Key = RandomStringUtils.randomAlphabetic(8)
        val tag2Value = RandomStringUtils.randomAlphabetic(8)
        every { campaign.campaignKey } returns campaignKey
        campaignMeterRegistry.init(campaign)
        val gauge = mockk<Gauge>()
        every { meterRegistry.gauge(any()) } returns gauge

        // when
        val result = campaignMeterRegistry.gauge(
            meterName, "scenario", scenario, "step", step,
            tag1Key, tag1Value, tag2Key, tag2Value,
            "tag-1", "value-1", "tag-2", "value-2"
        )

        // then
        assertThat(result).isSameAs(gauge)
        verifyOnce {
            meterRegistry.gauge(
                Meter.Id(
                    tags = mapOf(
                        tag1Key to tag1Value,
                        tag2Key to tag2Value,
                        "tag-1" to "value-1",
                        "tag-2" to "value-2",
                        "campaign" to campaignKey,
                        "scenario" to scenario,
                        "step" to step
                    ),
                    type = MeterType.GAUGE,
                    meterName = meterName
                )
            )
        }
        confirmVerified(meterRegistry, gauge)
    }

    @Test
    internal fun `should create a timer with percentiles`() = testDispatcherProvider.run {
        // given
        every { factoryConfiguration.tags } returns mapOf("tag-1" to "value-1", "tag-2" to "value-2")
        every { factoryConfiguration.zone } returns "at"
        val campaignMeterRegistry = CampaignMeterRegistryFacadeImpl(
            publisherFactories = emptyList(),
            meterRegistry = meterRegistry,
            factoryConfiguration = factoryConfiguration,
            measurementConfiguration = measurementConfiguration,
            step = Duration.ofSeconds(10),
            coroutineScope = this
        )
        val campaignKey = RandomStringUtils.randomAlphabetic(8)
        val scenario = RandomStringUtils.randomAlphabetic(8)
        val step = RandomStringUtils.randomAlphabetic(8)
        val meterName = RandomStringUtils.randomAlphabetic(8)
        val tag1Key = RandomStringUtils.randomAlphabetic(8)
        val tag1Value = RandomStringUtils.randomAlphabetic(8)
        val tag2Key = RandomStringUtils.randomAlphabetic(8)
        val tag2Value = RandomStringUtils.randomAlphabetic(8)
        every { campaign.campaignKey } returns campaignKey
        campaignMeterRegistry.init(campaign)
        val timer = mockk<Timer>()
        every { meterRegistry.timer(any(), any()) } returns timer

        // when
        val result = campaignMeterRegistry.timer(
            scenarioName = scenario,
            stepName = step,
            name = meterName,
            tags = mapOf(tag1Key to tag1Value, tag2Key to tag2Value),
            percentiles = listOf(50.0, 75.0, 52.65)
        )

        // then
        assertThat(result).isSameAs(timer)
        verifyOnce {
            meterRegistry.timer(
                Meter.Id(
                    tags = mapOf(
                        tag1Key to tag1Value,
                        tag2Key to tag2Value,
                        "tag-1" to "value-1",
                        "tag-2" to "value-2",
                        "campaign" to campaignKey,
                        "scenario" to scenario,
                        "step" to step
                    ),
                    type = MeterType.TIMER,
                    meterName = meterName
                ),
                percentiles = setOf(50.0, 75.0, 52.65)
            )
        }
        confirmVerified(meterRegistry, timer)
    }

    @Test
    internal fun `should create a timer without percentiles`() = testDispatcherProvider.run {
        // given
        every { factoryConfiguration.tags } returns mapOf("tag-1" to "value-1", "tag-2" to "value-2")
        every { factoryConfiguration.zone } returns "at"
        val campaignMeterRegistry = CampaignMeterRegistryFacadeImpl(
            publisherFactories = emptyList(),
            meterRegistry = meterRegistry,
            factoryConfiguration = factoryConfiguration,
            measurementConfiguration = measurementConfiguration,
            step = Duration.ofSeconds(10),
            coroutineScope = this
        )
        val campaignKey = RandomStringUtils.randomAlphabetic(8)
        val scenario = RandomStringUtils.randomAlphabetic(8)
        val step = RandomStringUtils.randomAlphabetic(8)
        val meterName = RandomStringUtils.randomAlphabetic(8)
        val tag1Key = RandomStringUtils.randomAlphabetic(8)
        val tag1Value = RandomStringUtils.randomAlphabetic(8)
        val tag2Key = RandomStringUtils.randomAlphabetic(8)
        val tag2Value = RandomStringUtils.randomAlphabetic(8)
        every { campaign.campaignKey } returns campaignKey
        campaignMeterRegistry.init(campaign)
        val timer = mockk<Timer>()
        every { meterRegistry.timer(any(), any()) } returns timer
        every { measurementConfiguration.timer.percentiles } returns listOf(42.52, 63.33)

        // when
        val result = campaignMeterRegistry.timer(
            scenarioName = scenario,
            stepName = step,
            name = meterName,
            tags = mapOf(tag1Key to tag1Value, tag2Key to tag2Value)
        )

        // then
        assertThat(result).isSameAs(timer)
        verifyOnce {
            meterRegistry.timer(
                Meter.Id(
                    tags = mapOf(
                        tag1Key to tag1Value,
                        tag2Key to tag2Value,
                        "tag-1" to "value-1",
                        "tag-2" to "value-2",
                        "campaign" to campaignKey,
                        "scenario" to scenario,
                        "step" to step
                    ),
                    type = MeterType.TIMER,
                    meterName = meterName
                ),
                percentiles = setOf(42.52, 63.33)
            )
        }
        confirmVerified(meterRegistry, timer)
    }

    @Test
    internal fun `should create a timer with tags only`() = testDispatcherProvider.run {
        // given
        every { factoryConfiguration.tags } returns mapOf("tag-1" to "value-1", "tag-2" to "value-2")
        every { factoryConfiguration.zone } returns "at"
        val campaignMeterRegistry = CampaignMeterRegistryFacadeImpl(
            publisherFactories = emptyList(),
            meterRegistry = meterRegistry,
            factoryConfiguration = factoryConfiguration,
            measurementConfiguration = measurementConfiguration,
            step = Duration.ofSeconds(10),
            coroutineScope = this
        )
        val campaignKey = RandomStringUtils.randomAlphabetic(8)
        val scenario = RandomStringUtils.randomAlphabetic(8)
        val step = RandomStringUtils.randomAlphabetic(8)
        val meterName = RandomStringUtils.randomAlphabetic(8)
        val tag1Key = RandomStringUtils.randomAlphabetic(8)
        val tag1Value = RandomStringUtils.randomAlphabetic(8)
        val tag2Key = RandomStringUtils.randomAlphabetic(8)
        val tag2Value = RandomStringUtils.randomAlphabetic(8)
        every { campaign.campaignKey } returns campaignKey
        campaignMeterRegistry.init(campaign)
        val timer = mockk<Timer>()
        every { meterRegistry.timer(any(), any()) } returns timer
        every { measurementConfiguration.timer.percentiles } returns listOf(42.52, 63.33)

        // when
        val result = campaignMeterRegistry.timer(
            meterName, "scenario", scenario, "step", step,
            tag1Key, tag1Value, tag2Key, tag2Value,
            "tag-1", "value-1", "tag-2", "value-2"
        )

        // then
        assertThat(result).isSameAs(timer)
        verifyOnce {
            meterRegistry.timer(
                Meter.Id(
                    tags = mapOf(
                        tag1Key to tag1Value,
                        tag2Key to tag2Value,
                        "tag-1" to "value-1",
                        "tag-2" to "value-2",
                        "campaign" to campaignKey,
                        "scenario" to scenario,
                        "step" to step
                    ),
                    type = MeterType.TIMER,
                    meterName = meterName
                ),
                setOf(42.52, 63.33)
            )
        }
        confirmVerified(meterRegistry, timer)
    }

    @Test
    internal fun `should create a summary with percentiles`() = testDispatcherProvider.run {
        // given
        every { factoryConfiguration.tags } returns mapOf("tag-1" to "value-1", "tag-2" to "value-2")
        every { factoryConfiguration.zone } returns "at"
        val campaignMeterRegistry = CampaignMeterRegistryFacadeImpl(
            publisherFactories = emptyList(),
            meterRegistry = meterRegistry,
            factoryConfiguration = factoryConfiguration,
            measurementConfiguration = measurementConfiguration,
            step = Duration.ofSeconds(10),
            coroutineScope = this
        )
        val campaignKey = RandomStringUtils.randomAlphabetic(8)
        val scenario = RandomStringUtils.randomAlphabetic(8)
        val step = RandomStringUtils.randomAlphabetic(8)
        val meterName = RandomStringUtils.randomAlphabetic(8)
        val tag1Key = RandomStringUtils.randomAlphabetic(8)
        val tag1Value = RandomStringUtils.randomAlphabetic(8)
        val tag2Key = RandomStringUtils.randomAlphabetic(8)
        val tag2Value = RandomStringUtils.randomAlphabetic(8)
        every { campaign.campaignKey } returns campaignKey
        campaignMeterRegistry.init(campaign)
        val summary = mockk<DistributionSummary>()
        every { meterRegistry.summary(any(), any()) } returns summary

        // when
        val result = campaignMeterRegistry.summary(
            scenarioName = scenario,
            stepName = step,
            name = meterName,
            tags = mapOf(tag1Key to tag1Value, tag2Key to tag2Value),
            percentiles = listOf(50.0, 75.0, 52.65)
        )

        // then
        assertThat(result).isSameAs(summary)
        verifyOnce {
            meterRegistry.summary(
                Meter.Id(
                    tags = mapOf(
                        tag1Key to tag1Value,
                        tag2Key to tag2Value,
                        "tag-1" to "value-1",
                        "tag-2" to "value-2",
                        "campaign" to campaignKey,
                        "scenario" to scenario,
                        "step" to step
                    ),
                    type = MeterType.DISTRIBUTION_SUMMARY,
                    meterName = meterName
                ),
                percentiles = setOf(50.0, 75.0, 52.65)
            )
        }
        confirmVerified(meterRegistry, summary)
    }

    @Test
    internal fun `should create a summary without percentiles`() = testDispatcherProvider.run {
        // given
        every { factoryConfiguration.tags } returns mapOf("tag-1" to "value-1", "tag-2" to "value-2")
        every { factoryConfiguration.zone } returns "at"
        val campaignMeterRegistry = CampaignMeterRegistryFacadeImpl(
            publisherFactories = emptyList(),
            meterRegistry = meterRegistry,
            factoryConfiguration = factoryConfiguration,
            measurementConfiguration = measurementConfiguration,
            step = Duration.ofSeconds(10),
            coroutineScope = this
        )
        val campaignKey = RandomStringUtils.randomAlphabetic(8)
        val scenario = RandomStringUtils.randomAlphabetic(8)
        val step = RandomStringUtils.randomAlphabetic(8)
        val meterName = RandomStringUtils.randomAlphabetic(8)
        val tag1Key = RandomStringUtils.randomAlphabetic(8)
        val tag1Value = RandomStringUtils.randomAlphabetic(8)
        val tag2Key = RandomStringUtils.randomAlphabetic(8)
        val tag2Value = RandomStringUtils.randomAlphabetic(8)
        every { campaign.campaignKey } returns campaignKey
        campaignMeterRegistry.init(campaign)
        val summary = mockk<DistributionSummary>()
        every { meterRegistry.summary(any(), any()) } returns summary
        every { measurementConfiguration.summary.percentiles } returns listOf(42.52, 63.33)

        // when
        val result = campaignMeterRegistry.summary(
            scenarioName = scenario,
            stepName = step,
            name = meterName,
            tags = mapOf(tag1Key to tag1Value, tag2Key to tag2Value)
        )

        // then
        assertThat(result).isSameAs(summary)
        verifyOnce {
            meterRegistry.summary(
                Meter.Id(
                    tags = mapOf(
                        tag1Key to tag1Value,
                        tag2Key to tag2Value,
                        "tag-1" to "value-1",
                        "tag-2" to "value-2",
                        "campaign" to campaignKey,
                        "scenario" to scenario,
                        "step" to step
                    ),
                    type = MeterType.DISTRIBUTION_SUMMARY,
                    meterName = meterName
                ),
                percentiles = setOf(42.52, 63.33)
            )
        }
        confirmVerified(meterRegistry, summary)
    }

    @Test
    internal fun `should create a summary with tags only`() = testDispatcherProvider.run {
        // given
        every { factoryConfiguration.tags } returns mapOf("tag-1" to "value-1", "tag-2" to "value-2")
        every { factoryConfiguration.zone } returns "at"
        val campaignMeterRegistry = CampaignMeterRegistryFacadeImpl(
            publisherFactories = emptyList(),
            meterRegistry = meterRegistry,
            factoryConfiguration = factoryConfiguration,
            measurementConfiguration = measurementConfiguration,
            step = Duration.ofSeconds(10),
            coroutineScope = this
        )
        val campaignKey = RandomStringUtils.randomAlphabetic(8)
        val scenario = RandomStringUtils.randomAlphabetic(8)
        val step = RandomStringUtils.randomAlphabetic(8)
        val meterName = RandomStringUtils.randomAlphabetic(8)
        val tag1Key = RandomStringUtils.randomAlphabetic(8)
        val tag1Value = RandomStringUtils.randomAlphabetic(8)
        val tag2Key = RandomStringUtils.randomAlphabetic(8)
        val tag2Value = RandomStringUtils.randomAlphabetic(8)
        every { campaign.campaignKey } returns campaignKey
        campaignMeterRegistry.init(campaign)
        val summary = mockk<DistributionSummary>()
        every { meterRegistry.summary(any(), any()) } returns summary
        every { measurementConfiguration.summary.percentiles } returns listOf(42.52, 63.33)

        // when
        val result = campaignMeterRegistry.summary(
            meterName, "scenario", scenario, "step", step,
            tag1Key, tag1Value, tag2Key, tag2Value,
            "tag-1", "value-1", "tag-2", "value-2"
        )

        // then
        assertThat(result).isSameAs(summary)
        verifyOnce {
            meterRegistry.summary(
                Meter.Id(
                    tags = mapOf(
                        tag1Key to tag1Value,
                        tag2Key to tag2Value,
                        "tag-1" to "value-1",
                        "tag-2" to "value-2",
                        "campaign" to campaignKey,
                        "scenario" to scenario,
                        "step" to step
                    ),
                    type = MeterType.DISTRIBUTION_SUMMARY,
                    meterName = meterName
                ),
                setOf(42.52, 63.33)
            )
        }
        confirmVerified(meterRegistry, summary)
    }

    @Test
    internal fun `should create a throughput with percentiles`() = testDispatcherProvider.run {
        // given
        every { factoryConfiguration.tags } returns mapOf("tag-1" to "value-1", "tag-2" to "value-2")
        every { factoryConfiguration.zone } returns "at"
        val campaignMeterRegistry = CampaignMeterRegistryFacadeImpl(
            publisherFactories = emptyList(),
            meterRegistry = meterRegistry,
            factoryConfiguration = factoryConfiguration,
            measurementConfiguration = measurementConfiguration,
            step = Duration.ofSeconds(10),
            coroutineScope = this
        )
        val campaignKey = RandomStringUtils.randomAlphabetic(8)
        val scenario = RandomStringUtils.randomAlphabetic(8)
        val step = RandomStringUtils.randomAlphabetic(8)
        val meterName = RandomStringUtils.randomAlphabetic(8)
        val tag1Key = RandomStringUtils.randomAlphabetic(8)
        val tag1Value = RandomStringUtils.randomAlphabetic(8)
        val tag2Key = RandomStringUtils.randomAlphabetic(8)
        val tag2Value = RandomStringUtils.randomAlphabetic(8)
        every { campaign.campaignKey } returns campaignKey
        campaignMeterRegistry.init(campaign)
        val throughput = mockk<Throughput>()
        every { meterRegistry.throughput(any(), any(), any()) } returns throughput

        // when
        val result = campaignMeterRegistry.throughput(
            scenarioName = scenario,
            stepName = step,
            name = meterName,
            tags = mapOf(tag1Key to tag1Value, tag2Key to tag2Value),
            percentiles = listOf(50.0, 75.0, 52.65)
        )

        // then
        assertThat(result).isSameAs(throughput)
        verifyOnce {
            meterRegistry.throughput(
                Meter.Id(
                    tags = mapOf(
                        tag1Key to tag1Value,
                        tag2Key to tag2Value,
                        "tag-1" to "value-1",
                        "tag-2" to "value-2",
                        "campaign" to campaignKey,
                        "scenario" to scenario,
                        "step" to step
                    ),
                    type = MeterType.THROUGHPUT,
                    meterName = meterName
                ),
                unit = ChronoUnit.SECONDS,
                percentiles = setOf(50.0, 75.0, 52.65)
            )
        }
        confirmVerified(meterRegistry, throughput)
    }

    @Test
    internal fun `should create a throughput with percentiles and configured unit interval`() =
        testDispatcherProvider.run {
            // given
            every { factoryConfiguration.tags } returns mapOf("tag-1" to "value-1", "tag-2" to "value-2")
            every { factoryConfiguration.zone } returns "at"
            val campaignMeterRegistry = CampaignMeterRegistryFacadeImpl(
                publisherFactories = emptyList(),
                meterRegistry = meterRegistry,
                factoryConfiguration = factoryConfiguration,
                measurementConfiguration = measurementConfiguration,
                step = Duration.ofSeconds(10),
                coroutineScope = this
            )
            val campaignKey = RandomStringUtils.randomAlphabetic(8)
            val scenario = RandomStringUtils.randomAlphabetic(8)
            val step = RandomStringUtils.randomAlphabetic(8)
            val meterName = RandomStringUtils.randomAlphabetic(8)
            val tag1Key = RandomStringUtils.randomAlphabetic(8)
            val tag1Value = RandomStringUtils.randomAlphabetic(8)
            val tag2Key = RandomStringUtils.randomAlphabetic(8)
            val tag2Value = RandomStringUtils.randomAlphabetic(8)
            every { campaign.campaignKey } returns campaignKey
            campaignMeterRegistry.init(campaign)
            val throughput = mockk<Throughput>()
            every { meterRegistry.throughput(any(), any(), any()) } returns throughput

            // when
            val result = campaignMeterRegistry.throughput(
                scenarioName = scenario,
                stepName = step,
                name = meterName,
                tags = mapOf(tag1Key to tag1Value, tag2Key to tag2Value),
                percentiles = listOf(50.0, 75.0, 52.65),
                unit = ChronoUnit.MINUTES
            )

            // then
            assertThat(result).isSameAs(throughput)
            verifyOnce {
                meterRegistry.throughput(
                    Meter.Id(
                        tags = mapOf(
                            tag1Key to tag1Value,
                            tag2Key to tag2Value,
                            "tag-1" to "value-1",
                            "tag-2" to "value-2",
                            "campaign" to campaignKey,
                            "scenario" to scenario,
                            "step" to step
                        ),
                        type = MeterType.THROUGHPUT,
                        meterName = meterName
                    ),
                    unit = ChronoUnit.MINUTES,
                    percentiles = setOf(50.0, 75.0, 52.65)
                )
            }
            confirmVerified(meterRegistry, throughput)
        }

    @Test
    internal fun `should create a throughput without percentiles`() = testDispatcherProvider.run {
        // given
        every { factoryConfiguration.tags } returns mapOf("tag-1" to "value-1", "tag-2" to "value-2")
        every { factoryConfiguration.zone } returns "at"
        val campaignMeterRegistry = CampaignMeterRegistryFacadeImpl(
            publisherFactories = emptyList(),
            meterRegistry = meterRegistry,
            factoryConfiguration = factoryConfiguration,
            measurementConfiguration = measurementConfiguration,
            step = Duration.ofSeconds(10),
            coroutineScope = this
        )
        val campaignKey = RandomStringUtils.randomAlphabetic(8)
        val scenario = RandomStringUtils.randomAlphabetic(8)
        val step = RandomStringUtils.randomAlphabetic(8)
        val meterName = RandomStringUtils.randomAlphabetic(8)
        val tag1Key = RandomStringUtils.randomAlphabetic(8)
        val tag1Value = RandomStringUtils.randomAlphabetic(8)
        val tag2Key = RandomStringUtils.randomAlphabetic(8)
        val tag2Value = RandomStringUtils.randomAlphabetic(8)
        every { campaign.campaignKey } returns campaignKey
        campaignMeterRegistry.init(campaign)
        val throughput = mockk<Throughput>()
        every { meterRegistry.throughput(any(), any(), any()) } returns throughput
        every { measurementConfiguration.throughput.percentiles } returns listOf(42.52, 63.33)

        // when
        val result = campaignMeterRegistry.throughput(
            scenarioName = scenario,
            stepName = step,
            name = meterName,
            tags = mapOf(tag1Key to tag1Value, tag2Key to tag2Value)
        )

        // then
        assertThat(result).isSameAs(throughput)
        verifyOnce {
            meterRegistry.throughput(
                Meter.Id(
                    tags = mapOf(
                        tag1Key to tag1Value,
                        tag2Key to tag2Value,
                        "tag-1" to "value-1",
                        "tag-2" to "value-2",
                        "campaign" to campaignKey,
                        "scenario" to scenario,
                        "step" to step
                    ),
                    type = MeterType.THROUGHPUT,
                    meterName = meterName
                ),
                ChronoUnit.SECONDS,
                percentiles = setOf(42.52, 63.33)
            )
        }
        confirmVerified(meterRegistry, throughput)
    }

    @Test
    internal fun `should create a throughput with tags only`() = testDispatcherProvider.run {
        // given
        every { factoryConfiguration.tags } returns mapOf("tag-1" to "value-1", "tag-2" to "value-2")
        every { factoryConfiguration.zone } returns "at"
        val campaignMeterRegistry = CampaignMeterRegistryFacadeImpl(
            publisherFactories = emptyList(),
            meterRegistry = meterRegistry,
            factoryConfiguration = factoryConfiguration,
            measurementConfiguration = measurementConfiguration,
            step = Duration.ofSeconds(10),
            coroutineScope = this
        )
        val campaignKey = RandomStringUtils.randomAlphabetic(8)
        val scenario = RandomStringUtils.randomAlphabetic(8)
        val step = RandomStringUtils.randomAlphabetic(8)
        val meterName = RandomStringUtils.randomAlphabetic(8)
        val tag1Key = RandomStringUtils.randomAlphabetic(8)
        val tag1Value = RandomStringUtils.randomAlphabetic(8)
        val tag2Key = RandomStringUtils.randomAlphabetic(8)
        val tag2Value = RandomStringUtils.randomAlphabetic(8)
        every { campaign.campaignKey } returns campaignKey
        campaignMeterRegistry.init(campaign)
        val throughput = mockk<Throughput>()
        every { meterRegistry.throughput(any(), any(), any()) } returns throughput
        every { measurementConfiguration.throughput.percentiles } returns listOf(42.52, 63.33)

        // when
        val result = campaignMeterRegistry.throughput(
            meterName, "scenario", scenario, "step", step,
            tag1Key, tag1Value, tag2Key, tag2Value,
            "tag-1", "value-1", "tag-2", "value-2"
        )

        // then
        assertThat(result).isSameAs(throughput)
        verifyOnce {
            meterRegistry.throughput(
                Meter.Id(
                    tags = mapOf(
                        tag1Key to tag1Value,
                        tag2Key to tag2Value,
                        "tag-1" to "value-1",
                        "tag-2" to "value-2",
                        "campaign" to campaignKey,
                        "scenario" to scenario,
                        "step" to step
                    ),
                    type = MeterType.THROUGHPUT,
                    meterName = meterName
                ),
                unit = ChronoUnit.SECONDS,
                setOf(42.52, 63.33)
            )
        }
        confirmVerified(meterRegistry, throughput)
    }

    @Test
    internal fun `should create a rate with tags only`() = testDispatcherProvider.run {
        // given
        every { factoryConfiguration.tags } returns mapOf("tag-1" to "value-1", "tag-2" to "value-2")
        every { factoryConfiguration.zone } returns "at"
        val campaignMeterRegistry = CampaignMeterRegistryFacadeImpl(
            publisherFactories = emptyList(),
            meterRegistry = meterRegistry,
            factoryConfiguration = factoryConfiguration,
            measurementConfiguration = measurementConfiguration,
            step = Duration.ofSeconds(10),
            coroutineScope = this
        )
        val campaignKey = RandomStringUtils.randomAlphabetic(8)
        val scenario = RandomStringUtils.randomAlphabetic(8)
        val step = RandomStringUtils.randomAlphabetic(8)
        val meterName = RandomStringUtils.randomAlphabetic(8)
        val tag1Key = RandomStringUtils.randomAlphabetic(8)
        val tag1Value = RandomStringUtils.randomAlphabetic(8)
        val tag2Key = RandomStringUtils.randomAlphabetic(8)
        val tag2Value = RandomStringUtils.randomAlphabetic(8)
        every { campaign.campaignKey } returns campaignKey
        campaignMeterRegistry.init(campaign)
        val rate = mockk<Rate>()
        every { meterRegistry.rate(any()) } returns rate

        // when
        val result = campaignMeterRegistry.rate(
            meterName, "scenario", scenario, "step", step,
            tag1Key, tag1Value, tag2Key, tag2Value,
            "tag-1", "value-1", "tag-2", "value-2"
        )

        // then
        assertThat(result).isSameAs(rate)
        verifyOnce {
            meterRegistry.rate(
                Meter.Id(
                    tags = mapOf(
                        tag1Key to tag1Value,
                        tag2Key to tag2Value,
                        "tag-1" to "value-1",
                        "tag-2" to "value-2",
                        "campaign" to campaignKey,
                        "scenario" to scenario,
                        "step" to step
                    ),
                    type = MeterType.RATE,
                    meterName = meterName
                )
            )
        }
        confirmVerified(meterRegistry, rate)
    }

    @Test
    internal fun `should create a rate`() = testDispatcherProvider.run {
        // given
        every { factoryConfiguration.tags } returns mapOf("tag-1" to "value-1", "tag-2" to "value-2")
        every { factoryConfiguration.zone } returns "at"
        val campaignMeterRegistry = CampaignMeterRegistryFacadeImpl(
            publisherFactories = emptyList(),
            meterRegistry = meterRegistry,
            factoryConfiguration = factoryConfiguration,
            measurementConfiguration = measurementConfiguration,
            step = Duration.ofSeconds(10),
            coroutineScope = this
        )
        val campaignKey = RandomStringUtils.randomAlphabetic(8)
        val scenario = RandomStringUtils.randomAlphabetic(8)
        val step = RandomStringUtils.randomAlphabetic(8)
        val meterName = RandomStringUtils.randomAlphabetic(8)
        val tag1Key = RandomStringUtils.randomAlphabetic(8)
        val tag1Value = RandomStringUtils.randomAlphabetic(8)
        val tag2Key = RandomStringUtils.randomAlphabetic(8)
        val tag2Value = RandomStringUtils.randomAlphabetic(8)
        every { campaign.campaignKey } returns campaignKey
        campaignMeterRegistry.init(campaign)
        val rate = mockk<Rate>()
        every { meterRegistry.rate(any()) } returns rate

        // when
        val result = campaignMeterRegistry.rate(
            scenarioName = scenario,
            stepName = step,
            name = meterName,
            tags = mapOf(tag1Key to tag1Value, tag2Key to tag2Value)
        )

        // then
        assertThat(result).isSameAs(rate)
        verifyOnce {
            meterRegistry.rate(
                Meter.Id(
                    tags = mapOf(
                        tag1Key to tag1Value,
                        tag2Key to tag2Value,
                        "tag-1" to "value-1",
                        "tag-2" to "value-2",
                        "campaign" to campaignKey,
                        "scenario" to scenario,
                        "step" to step
                    ),
                    type = MeterType.RATE,
                    meterName = meterName
                )
            )
        }
        confirmVerified(meterRegistry, rate)
    }
}