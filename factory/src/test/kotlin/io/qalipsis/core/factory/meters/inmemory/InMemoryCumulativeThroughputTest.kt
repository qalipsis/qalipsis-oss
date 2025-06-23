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

package io.qalipsis.core.factory.meters.inmemory

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Statistic
import io.qalipsis.core.factory.meters.MeterSnapshotImpl
import io.qalipsis.core.reporter.MeterReporter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@WithMockk
internal class InMemoryCumulativeThroughputTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var meterReporter: MeterReporter

    @AfterEach
    fun tearDown() {
        unmockkStatic(Clock::class)
    }

    @Test
    internal fun `should return the total of throughput of the observations`() = testDispatcherProvider.run {
        // given
        val id = Meter.Id("any-test", type = MeterType.THROUGHPUT, tags = mapOf("a" to "b", "c" to "d"))
        val throughput = InMemoryCumulativeThroughput(id, meterReporter, ChronoUnit.SECONDS, listOf())
        throughput.record(5.0)

        // when
        throughput.record(4.0)
        throughput.record(2.0)
        throughput.record(6.0)
        throughput.record()
        throughput.record(2.0)

        // then
        assertThat(throughput.total()).isEqualTo(20.0)
    }

    @Test
    internal fun `should return the current value at any time`() = testDispatcherProvider.run {
        // given
        val id = Meter.Id("any-test", type = MeterType.THROUGHPUT, tags = mapOf("a" to "b", "c" to "d"))
        mockkStatic(Clock::class)
        val instant0 = Clock.fixed(Instant.EPOCH, ZoneId.systemDefault())
        val instant1 = Clock.fixed(Instant.EPOCH.plusMillis(1100), ZoneId.systemDefault())
        val instant2 =
            Clock.fixed(Instant.EPOCH.plusMillis(2100), ZoneId.systemDefault()) // Here there is a gap of 2 time slots.
        every { Clock.systemUTC() } returns instant0
        val throughput = InMemoryCumulativeThroughput(id, meterReporter, ChronoUnit.SECONDS, listOf())

        // when
        throughput.record(5.0)
        throughput.record(4.0)
        throughput.record(3.0)
        assertThat(throughput.current()).isEqualTo(0.0)

        // when
        every { Clock.systemUTC() } returns instant1
        throughput.record(6.0)
        throughput.record(2.0)
        every { Clock.systemUTC() } returns instant2
        throughput.record(1.0)

        // then
        assertThat(throughput.current()).isEqualTo(8.0)
    }

    @Test
    internal fun `should return the mean of the observations`() = testDispatcherProvider.run {
        // given
        val id = Meter.Id("any-test", type = MeterType.THROUGHPUT, tags = mapOf("a" to "b", "c" to "d"))
        mockkStatic(Clock::class)
        val instant0 = Clock.fixed(Instant.EPOCH, ZoneId.systemDefault())
        val instant1 = Clock.fixed(Instant.EPOCH.plusMillis(1100), ZoneId.systemDefault())
        val instant2 =
            Clock.fixed(Instant.EPOCH.plusMillis(3100), ZoneId.systemDefault()) // Here there is a gap of 2 time slots.
        every { Clock.systemUTC() } returns instant0
        val throughput = InMemoryCumulativeThroughput(id, meterReporter, ChronoUnit.SECONDS, listOf())

        // when
        throughput.record(5.0)
        throughput.record(4.0)
        throughput.record(3.0)
        assertThat(throughput.mean()).isEqualTo(12.0)

        // when
        every { Clock.systemUTC() } returns instant1
        throughput.record(6.0)
        throughput.record(3.0)
        every { Clock.systemUTC() } returns instant2
        throughput.record(1.0)

        // then
        assertThat(throughput.mean()).isEqualTo(7.0)
    }


    @Test
    internal fun `should return the max of the observations`() = testDispatcherProvider.run {
        // given
        val id = Meter.Id("any-test", type = MeterType.THROUGHPUT, tags = mapOf("a" to "b", "c" to "d"))
        mockkStatic(Clock::class)
        val instant0 = Clock.fixed(Instant.EPOCH, ZoneId.systemDefault())
        val instant1 = Clock.fixed(Instant.EPOCH.plusMillis(1100), ZoneId.systemDefault())
        val instant2 =
            Clock.fixed(Instant.EPOCH.plusMillis(3100), ZoneId.systemDefault()) // Here there is a gap of 2 time slots.
        val instant3 = Clock.fixed(Instant.EPOCH.plusMillis(4100), ZoneId.systemDefault())
        every { Clock.systemUTC() } returns instant0
        val throughput = InMemoryCumulativeThroughput(id, meterReporter, ChronoUnit.SECONDS, listOf())

        // when
        throughput.record(5.0)
        throughput.record(4.0)
        throughput.record(3.0)

        // then
        assertThat(throughput.max()).isEqualTo(12.0)

        // when
        every { Clock.systemUTC() } returns instant1
        throughput.record(6.0)
        throughput.record(12.0)
        every { Clock.systemUTC() } returns instant2

        // then
        assertThat(throughput.max()).isEqualTo(12.0)

        // when
        every { Clock.systemUTC() } returns instant3
        throughput.record()

        // then
        assertThat(throughput.max()).isEqualTo(18.0)
    }

    @Test
    internal fun `should not return any observationPoint from the collected observations when no percentile is specified`() {
        // given
        val id = Meter.Id("any-test", type = MeterType.THROUGHPUT, tags = mapOf("a" to "b", "c" to "d"))
        val throughput = InMemoryCumulativeThroughput(id, meterReporter, ChronoUnit.SECONDS, listOf())
        throughput.record(33.0)
        throughput.record(68.9)
        throughput.record(2.0)
        throughput.record(12.0)
        throughput.record(45.6)
        throughput.record(77.0)

        // when
        val result = throughput.percentile(75.0)

        // then
        assertThat(result).isEqualTo(0.0)
    }


    @Test
    internal fun `should return the observationPoint of the collected observations`() {
        // given
        val id = Meter.Id("any-test", type = MeterType.THROUGHPUT, tags = mapOf("a" to "b", "c" to "d"))
        mockkStatic(Clock::class)
        val instant0 = Clock.fixed(Instant.EPOCH, ZoneId.systemDefault())
        val instant1 = Clock.fixed(Instant.EPOCH.plusMillis(1100), ZoneId.systemDefault())
        val instant2 =
            Clock.fixed(Instant.EPOCH.plusMillis(3100), ZoneId.systemDefault()) // Here there is a gap of 2 time slots.
        val instant3 = Clock.fixed(Instant.EPOCH.plusMillis(4100), ZoneId.systemDefault())
        every { Clock.systemUTC() } returns instant0
        val throughput = InMemoryCumulativeThroughput(id, meterReporter, ChronoUnit.SECONDS, listOf(75.0))

        // when
        throughput.record(11)
        throughput.record()
        throughput.record(8)
        throughput.record(3)
        every { Clock.systemUTC() } returns instant1

        // Start new time slot.
        throughput.record(9)

        // Start new time slot.
        every { Clock.systemUTC() } returns instant2
        throughput.record(4)
        throughput.record(7)
        throughput.record(11)

        // Start new time slot.
        every { Clock.systemUTC() } returns instant3
        throughput.record(8)
        throughput.record(3)
        throughput.record(15)
        throughput.record()

        //then
        assertThat(throughput.percentile(75.0)).isEqualTo(23.0)
    }

    @Test
    internal fun `should build the snapshot and reset`() = testDispatcherProvider.run {
        //given
        val id = Meter.Id("any-test", type = MeterType.THROUGHPUT, tags = mapOf("a" to "b", "c" to "d"))
        mockkStatic(Clock::class)
        val instant0 = Clock.fixed(Instant.EPOCH, ZoneId.systemDefault())
        val instant1 = Clock.fixed(Instant.EPOCH.plusMillis(1100), ZoneId.systemDefault())
        val instant2 =
            Clock.fixed(Instant.EPOCH.plusMillis(3100), ZoneId.systemDefault()) // Here there is a gap of 2 time slots.
        val instant3 = Clock.fixed(Instant.EPOCH.plusMillis(4100), ZoneId.systemDefault())
        every { Clock.systemUTC() } returns instant0
        val throughput = InMemoryCumulativeThroughput(id, meterReporter, ChronoUnit.SECONDS, listOf(75.0))

        // when
        throughput.record(11)
        throughput.record()
        throughput.record(8)
        throughput.record(3)
        every { Clock.systemUTC() } returns instant1

        // Start new time slot
        throughput.record(9)

        // Start new time slot
        every { Clock.systemUTC() } returns instant2
        throughput.record(4)
        throughput.record(7)
        throughput.record(11)

        // Start new time slot
        every { Clock.systemUTC() } returns instant3
        throughput.record(8)
        throughput.record(3)
        throughput.record(15)
        throughput.record()

        // when
        val now = Instant.now()
        var result = throughput.snapshot(now)

        //then
        assertThat(result).isInstanceOf(MeterSnapshotImpl::class).all {
            prop(MeterSnapshotImpl::meterId).isEqualTo(
                Meter.Id(
                    "any-test",
                    type = MeterType.THROUGHPUT,
                    tags = mapOf("a" to "b", "c" to "d", "scope" to "period")
                )
            )
            prop(MeterSnapshotImpl::timestamp).isEqualTo(now)
            prop(MeterSnapshotImpl::measurements).transform { it.toList() }.all {
                hasSize(5)
                index(0).all {
                    prop(Measurement::value).isEqualTo(22.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.VALUE)
                }
                index(1).all {
                    prop(Measurement::value).isEqualTo(13.5)
                    prop(Measurement::statistic).isEqualTo(Statistic.MEAN)
                }
                index(2).all {
                    prop(Measurement::value).isEqualTo(23.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.MAX)
                }
                index(3).all {
                    prop(Measurement::value).isEqualTo(81.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.TOTAL)
                }
                index(4).all {
                    isInstanceOf(DistributionMeasurementMetric::class).all {
                        prop(DistributionMeasurementMetric::value).isEqualTo(23.0)
                        prop(DistributionMeasurementMetric::statistic).isEqualTo(Statistic.PERCENTILE)
                        prop(DistributionMeasurementMetric::observationPoint).isEqualTo(75.0)
                    }
                }
            }
        }

        // when
        result = throughput.snapshot(now)

        //then
        assertThat(result).isInstanceOf(MeterSnapshotImpl::class).all {
            prop(MeterSnapshotImpl::meterId).isEqualTo(
                Meter.Id(
                    "any-test",
                    type = MeterType.THROUGHPUT,
                    tags = mapOf("a" to "b", "c" to "d", "scope" to "period")
                )
            )
            prop(MeterSnapshotImpl::timestamp).isEqualTo(now)
            prop(MeterSnapshotImpl::measurements).transform { it.toList() }.all {
                hasSize(5)
                index(0).all {
                    prop(Measurement::value).isEqualTo(0.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.VALUE)
                }
                index(1).all {
                    prop(Measurement::value).isEqualTo(0.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.MEAN)
                }
                index(2).all {
                    prop(Measurement::value).isEqualTo(0.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.MAX)
                }
                index(3).all {
                    prop(Measurement::value).isEqualTo(0.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.TOTAL)
                }
                index(4).all {
                    isInstanceOf(DistributionMeasurementMetric::class).all {
                        prop(DistributionMeasurementMetric::value).isEqualTo(Double.NaN)
                        prop(DistributionMeasurementMetric::statistic).isEqualTo(Statistic.PERCENTILE)
                        prop(DistributionMeasurementMetric::observationPoint).isEqualTo(75.0)
                    }
                }
            }
        }
    }

    @Test
    internal fun `should build the snapshot and keep on cumulating`() = testDispatcherProvider.run {
        // given
        val id = Meter.Id("any-test", type = MeterType.THROUGHPUT, tags = mapOf("a" to "b", "c" to "d"))
        mockkStatic(Clock::class)
        val instant0 = Clock.fixed(Instant.EPOCH, ZoneId.systemDefault())
        val instant1 = Clock.fixed(Instant.EPOCH.plusMillis(1100), ZoneId.systemDefault())
        val instant2 =
            Clock.fixed(Instant.EPOCH.plusMillis(3100), ZoneId.systemDefault()) // Here there is a gap of 2 time slots.
        val instant3 = Clock.fixed(Instant.EPOCH.plusMillis(4100), ZoneId.systemDefault())
        val instant4 = Clock.fixed(Instant.EPOCH.plusMillis(5100), ZoneId.systemDefault())
        every { Clock.systemUTC() } returns instant0
        val throughput = InMemoryCumulativeThroughput(id, meterReporter, ChronoUnit.SECONDS, listOf(75.0))

        // when
        throughput.record(11)
        throughput.record()
        throughput.record(8)
        throughput.record(3)
        every { Clock.systemUTC() } returns instant1

        // Start new time slot
        throughput.record(9)

        // Start new time slot
        every { Clock.systemUTC() } returns instant2
        throughput.record(4)
        throughput.record(7)
        throughput.record(11)

        // Start new time slot
        every { Clock.systemUTC() } returns instant3
        throughput.record(8)
        throughput.record(3)
        throughput.record(15)
        throughput.record()

        // when
        val now = Instant.now()
        var result = throughput.summarize(now)

        // then
        assertThat(result).isInstanceOf(MeterSnapshotImpl::class).all {
            prop(MeterSnapshotImpl::meterId).isEqualTo(
                Meter.Id(
                    "any-test",
                    type = MeterType.THROUGHPUT,
                    tags = mapOf("a" to "b", "c" to "d", "scope" to "campaign")
                )
            )
            prop(MeterSnapshotImpl::timestamp).isEqualTo(now)
            prop(MeterSnapshotImpl::measurements).transform { it.toList() }.all {
                hasSize(5)
                index(0).all {
                    prop(Measurement::value).isEqualTo(22.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.VALUE)
                }
                index(1).all {
                    prop(Measurement::value).isEqualTo(13.5)
                    prop(Measurement::statistic).isEqualTo(Statistic.MEAN)
                }
                index(2).all {
                    prop(Measurement::value).isEqualTo(23.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.MAX)
                }
                index(3).all {
                    prop(Measurement::value).isEqualTo(81.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.TOTAL)
                }
                index(4).all {
                    isInstanceOf(DistributionMeasurementMetric::class).all {
                        prop(DistributionMeasurementMetric::value).isEqualTo(23.0)
                        prop(DistributionMeasurementMetric::statistic).isEqualTo(Statistic.PERCENTILE)
                        prop(DistributionMeasurementMetric::observationPoint).isEqualTo(75.0)
                    }
                }
            }
        }

        // when
        throughput.record()
        throughput.record(10)
        throughput.record(13)
        every { Clock.systemUTC() } returns instant4
        throughput.record(15)
        result = throughput.summarize(now)

        // then
        assertThat(result).isInstanceOf(MeterSnapshotImpl::class).all {
            prop(MeterSnapshotImpl::meterId).isEqualTo(
                Meter.Id(
                    "any-test",
                    type = MeterType.THROUGHPUT,
                    tags = mapOf("a" to "b", "c" to "d", "scope" to "campaign")
                )
            )
            prop(MeterSnapshotImpl::timestamp).isEqualTo(now)
            prop(MeterSnapshotImpl::measurements).transform { it.toList() }.all {
                hasSize(5)
                index(0).all {
                    prop(Measurement::value).isEqualTo(51.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.VALUE)
                }
                index(1).all {
                    prop(Measurement::value).isEqualTo(21.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.MEAN)
                }
                index(2).all {
                    prop(Measurement::value).isEqualTo(51.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.MAX)
                }
                index(3).all {
                    prop(Measurement::value).isEqualTo(120.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.TOTAL)
                }
                index(4).all {
                    isInstanceOf(DistributionMeasurementMetric::class).all {
                        prop(DistributionMeasurementMetric::value).isEqualTo(23.0)
                        prop(DistributionMeasurementMetric::statistic).isEqualTo(Statistic.PERCENTILE)
                        prop(DistributionMeasurementMetric::observationPoint).isEqualTo(75.0)
                    }
                }
            }
        }
    }

}