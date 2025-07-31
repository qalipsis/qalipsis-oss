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
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Statistic
import io.qalipsis.core.factory.meters.MeterSnapshotImpl
import io.qalipsis.core.factory.meters.inmemory.catadioptre.measure
import io.qalipsis.core.factory.meters.inmemory.catadioptre.tDigest
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
internal class StepThroughputTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var meterReporter: MeterReporter

    @AfterEach
    fun tearDown() {
        unmockkStatic(Clock::class)
    }

    @Test
    internal fun `should return the total of the collected observations`() = testDispatcherProvider.runTest {
        //given
        val id = mockk<Meter.Id>()
        val throughput = StepThroughput(id, meterReporter, ChronoUnit.SECONDS, emptyList())

        // when
        throughput.record()
        throughput.record(8)
        throughput.record()
        throughput.record(7)
        throughput.record()
        throughput.record(1)

        //then
        assertThat(throughput.total()).isEqualTo(19.0)
    }

    @Test
    internal fun `should return the max of the collected observations`() = testDispatcherProvider.runTest {
        // given
        val id = mockk<Meter.Id>()
        mockkStatic(Clock::class)
        val instant0 = Clock.fixed(Instant.EPOCH, ZoneId.systemDefault())
        val instant1 = Clock.fixed(Instant.EPOCH.plusMillis(1100), ZoneId.systemDefault())
        val instant2 =
            Clock.fixed(Instant.EPOCH.plusMillis(3100), ZoneId.systemDefault()) // Here there is a gap of 2 time slots.
        val instant3 = Clock.fixed(Instant.EPOCH.plusMillis(4100), ZoneId.systemDefault())
        every { Clock.systemUTC() } returns instant0
        val throughput = StepThroughput(id, meterReporter, ChronoUnit.SECONDS, emptyList())

        // when
        throughput.record(11)
        throughput.record()
        throughput.record(8)

        // then
        assertThat(throughput.max()).isEqualTo(20.0) // Since this is the first time slot, the total should be returned.

        // when
        throughput.record(3)
        every { Clock.systemUTC() } returns instant1
        throughput.record(2.0) // Starts a new time slot.

        // then
        assertThat(throughput.max()).isEqualTo(23.0)

        // when
        every { Clock.systemUTC() } returns instant2
        throughput.record(35) // Starts a new time slot, jumping over one without hit.
        every { Clock.systemUTC() } returns instant3
        throughput.record() // Starts a new time slot, jumping over one without hit.

        // then
        assertThat(throughput.max()).isEqualTo(35.0)
    }

    @Test
    internal fun `should return the mean of the collected observations`() = testDispatcherProvider.runTest {
        //given
        val id = mockk<Meter.Id>()
        mockkStatic(Clock::class)
        val instant0 = Clock.fixed(Instant.EPOCH, ZoneId.systemDefault())
        val instant1 = Clock.fixed(Instant.EPOCH.plusMillis(1100), ZoneId.systemDefault())
        val instant2 =
            Clock.fixed(Instant.EPOCH.plusMillis(3100), ZoneId.systemDefault()) // Here there is a gap of 2 time slots.

        every { Clock.systemUTC() } returns instant0
        val throughput = StepThroughput(id, meterReporter, ChronoUnit.SECONDS, emptyList())

        // when
        throughput.record(11)
        throughput.record(8)

        //then
        assertThat(throughput.mean()).isEqualTo(19.0)

        // when
        every { Clock.systemUTC() } returns instant1
        throughput.record(3)
        throughput.record(6)
        throughput.record(8)
        every { Clock.systemUTC() } returns instant2
        throughput.record(4)

        //then
        assertThat(throughput.mean()).isEqualTo(12.0)
    }

    @Test
    internal fun `should return the current at any given time`() = testDispatcherProvider.runTest {
        // given
        val id = mockk<Meter.Id>()
        mockkStatic(Clock::class)
        val instant0 = Clock.fixed(Instant.EPOCH, ZoneId.systemDefault())
        val instant1 = Clock.fixed(Instant.EPOCH.plusMillis(1100), ZoneId.systemDefault())
        val instant2 =
            Clock.fixed(Instant.EPOCH.plusMillis(3100), ZoneId.systemDefault()) // Here there is a gap of 2 time slots.
        every { Clock.systemUTC() } returns instant0
        val throughput = StepThroughput(id, meterReporter, ChronoUnit.SECONDS, emptyList())

        // when
        throughput.record(11)
        throughput.record()
        throughput.record(8)

        // then
        assertThat(throughput.current()).isEqualTo(20.0) // Since this is the first time slot, the total should be returned.

        // when
        throughput.record(3)
        every { Clock.systemUTC() } returns instant1
        throughput.record(2.0) // Starts a new time slot.

        // then
        assertThat(throughput.current()).isEqualTo(23.0) // Should return the total of the previous time slot.

        // when
        every { Clock.systemUTC() } returns instant2
        throughput.record() // Starts a new time slot, jumping over one without hit.

        // then
        assertThat(throughput.current()).isEqualTo(0.0) // Should return the total of the previous time slot.
    }


    @Test
    internal fun `should not return any observationPoint when no percentile is specified`() {
        //given
        val id = mockk<Meter.Id>()
        val throughput = StepThroughput(id, meterReporter, ChronoUnit.SECONDS, emptyList())
        throughput.record(3)
        throughput.record(8)
        throughput.record(2)
        throughput.record(10)
        throughput.record(5)
        throughput.record(7)

        //when
        val result = throughput.percentile(75.0)

        //then
        assertThat(result).isEqualTo(0.0)
        assertThat(throughput.tDigest()).isNull()
    }

    @Test
    internal fun `should return the observationPoint of the collected observations`() {
        //given
        val id = mockk<Meter.Id>()
        mockkStatic(Clock::class)
        val instant0 = Clock.fixed(Instant.EPOCH, ZoneId.systemDefault())
        val instant1 = Clock.fixed(Instant.EPOCH.plusMillis(1100), ZoneId.systemDefault())
        val instant2 =
            Clock.fixed(Instant.EPOCH.plusMillis(3100), ZoneId.systemDefault()) // Here there is a gap of 2 time slots.
        val instant3 = Clock.fixed(Instant.EPOCH.plusMillis(4100), ZoneId.systemDefault())
        every { Clock.systemUTC() } returns instant0
        val throughput = StepThroughput(id, meterReporter, ChronoUnit.SECONDS, listOf(75.0))

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

        //when
        val result = throughput.percentile(75.0)

        //then
        assertThat(result).isEqualTo(23.0)
    }


    @Test
    internal fun `should generate the measures and reset`() = testDispatcherProvider.run {
        //given
        val id = mockk<Meter.Id>()
        mockkStatic(Clock::class)
        val instant0 = Clock.fixed(Instant.EPOCH, ZoneId.systemDefault())
        val instant1 = Clock.fixed(Instant.EPOCH.plusMillis(1100), ZoneId.systemDefault())
        val instant2 =
            Clock.fixed(Instant.EPOCH.plusMillis(3100), ZoneId.systemDefault()) // Here there is a gap of 2 time slots.
        val instant3 = Clock.fixed(Instant.EPOCH.plusMillis(4100), ZoneId.systemDefault())
        every { Clock.systemUTC() } returns instant0
        val throughput = StepThroughput(id, meterReporter, ChronoUnit.SECONDS, listOf(75.0))

        //Start record
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
        var result = throughput.measure().toList()

        //then
        assertThat(result).isNotNull().all {
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

        // when
        result = throughput.measure().toList()

        //then
        assertThat(result).isNotNull().all {
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
        val throughput = StepThroughput(id, meterReporter, ChronoUnit.SECONDS, listOf(75.0))

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

        //when
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
}