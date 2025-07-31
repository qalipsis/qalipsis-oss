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
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Statistic
import io.qalipsis.core.factory.meters.MeterSnapshotImpl
import io.qalipsis.core.reporter.MeterReporter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

@WithMockk
internal class InMemoryCumulativeDistributionSummaryTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var meterReporter: MeterReporter

    @Test
    internal fun `should increment the counter by 1 when record is called`() {
        //given
        val id = mockk<Meter.Id>()
        val summary = InMemoryCumulativeDistributionSummary(id, meterReporter, listOf())
        summary.record(5.0)
        summary.record(6.0)
        summary.record(2.0)

        //when
        val result = summary.count()

        //then
        assertThat(result).isEqualTo(3)
    }

    @Test
    internal fun `should return the total of the collected observations`() {
        //given
        val id = mockk<Meter.Id>()
        val summary = InMemoryCumulativeDistributionSummary(id, meterReporter, listOf())
        summary.record(5.0)
        summary.record(6.0)
        summary.record(2.0)

        //when
        val result = summary.totalAmount()

        //then
        assertThat(result).isEqualTo(13.0)
    }

    @Test
    internal fun `should return the max of the collected observations`() {
        //given
        val id = mockk<Meter.Id>()
        val summary = InMemoryCumulativeDistributionSummary(id, meterReporter, listOf())
        summary.record(5.0)
        summary.record(6.0)
        summary.record(2.0)

        //when
        val result = summary.max()

        //then
        assertThat(result).isEqualTo(6.0)
    }

    @Test
    internal fun `should return the mean of the collected observations`() {
        //given
        val id = mockk<Meter.Id>()
        val summary = InMemoryCumulativeDistributionSummary(id, meterReporter, listOf())
        summary.record(5.0)
        summary.record(6.0)
        summary.record(5.5)

        //when
        val result = summary.mean()

        //then
        assertThat(result).isEqualTo(5.5)
    }

    @Test
    internal fun `should record collected observations`() {
        //given
        val id = mockk<Meter.Id>()
        val summary = InMemoryCumulativeDistributionSummary(id, meterReporter, listOf())

        // when
        summary.record(5.0)
        summary.record(11.2)
        summary.record(11.2)

        //then
        assertThat(summary.count()).transform { it.toDouble() }.isEqualTo(3.0)
        assertThat(summary.totalAmount()).isEqualTo(27.4)
    }

    @Test
    internal fun `should not return any observationPoint from the collected observations when no percentile is specified`() {
        //given
        val id = mockk<Meter.Id>()
        val summary = InMemoryCumulativeDistributionSummary(id, meterReporter, listOf())
        summary.record(33.0)
        summary.record(68.9)
        summary.record(2.0)
        summary.record(12.0)
        summary.record(45.6)
        summary.record(77.0)

        //when
        val result = summary.percentile(75.0)

        //then
        assertThat(result).isEqualTo(0.0)
    }

    @Test
    internal fun `should return the observationPoint of the collected observations`() {
        //given
        val id = mockk<Meter.Id>()
        val summary = InMemoryCumulativeDistributionSummary(id, meterReporter, listOf(50.0))
        summary.record(33.0)
        summary.record(68.9)
        summary.record(2.0)
        summary.record(12.0)
        summary.record(45.6)
        summary.record(77.0)

        //when
        val result = summary.percentile(75.0)

        //then
        assertThat(result).isEqualTo(68.9)
    }

    @Test
    internal fun `should build the snapshot and reset`() = testDispatcherProvider.run {
        //given
        val id = Meter.Id("any-test", type = MeterType.DISTRIBUTION_SUMMARY, tags = mapOf("a" to "b", "c" to "d"))
        val summary = InMemoryCumulativeDistributionSummary(id, meterReporter, listOf(75.0, 99.0))
        summary.record(33.0)
        summary.record(68.9)
        summary.record(2.0)
        summary.record(12.0)
        summary.record(45.6)
        summary.record(77.0)

        // when
        val now = Instant.now()
        var result = summary.snapshot(now)

        //then
        assertThat(result).isInstanceOf(MeterSnapshotImpl::class).all {
            prop(MeterSnapshotImpl::meterId).isEqualTo(
                Meter.Id(
                    "any-test",
                    type = MeterType.DISTRIBUTION_SUMMARY,
                    tags = mapOf("a" to "b", "c" to "d", "scope" to "period")
                )
            )
            prop(MeterSnapshotImpl::timestamp).isEqualTo(now)
            prop(MeterSnapshotImpl::measurements).transform { it.toList() }.all {
                hasSize(6)
                index(0).all {
                    prop(Measurement::value).isEqualTo(39.75)
                    prop(Measurement::statistic).isEqualTo(Statistic.MEAN)
                }
                index(1).all {
                    prop(Measurement::value).isEqualTo(6.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.COUNT)
                }
                index(2).all {
                    prop(Measurement::value).isEqualTo(238.5)
                    prop(Measurement::statistic).isEqualTo(Statistic.TOTAL)
                }
                index(3).all {
                    prop(Measurement::value).isEqualTo(77.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.MAX)
                }
                index(4).all {
                    isInstanceOf(DistributionMeasurementMetric::class).all {
                        prop(DistributionMeasurementMetric::value).isEqualTo(68.9)
                        prop(DistributionMeasurementMetric::statistic).isEqualTo(Statistic.PERCENTILE)
                        prop(DistributionMeasurementMetric::observationPoint).isEqualTo(75.0)
                    }
                }
                index(5).all {
                    isInstanceOf(DistributionMeasurementMetric::class).all {
                        prop(DistributionMeasurementMetric::value).isEqualTo(77.0)
                        prop(DistributionMeasurementMetric::statistic).isEqualTo(Statistic.PERCENTILE)
                        prop(DistributionMeasurementMetric::observationPoint).isEqualTo(99.0)
                    }
                }
            }
        }

        // when
        result = summary.snapshot(now)

        //then
        assertThat(result).isInstanceOf(MeterSnapshotImpl::class).all {
            prop(MeterSnapshotImpl::meterId).isEqualTo(
                Meter.Id(
                    "any-test",
                    type = MeterType.DISTRIBUTION_SUMMARY,
                    tags = mapOf("a" to "b", "c" to "d", "scope" to "period")
                )
            )
            prop(MeterSnapshotImpl::timestamp).isEqualTo(now)
            prop(MeterSnapshotImpl::measurements).transform { it.toList() }.all {
                hasSize(6)
                index(0).all {
                    prop(Measurement::value).isEqualTo(0.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.MEAN)
                }
                index(1).all {
                    prop(Measurement::value).isEqualTo(0.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.COUNT)
                }
                index(2).all {
                    prop(Measurement::value).isEqualTo(0.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.TOTAL)
                }
                index(3).all {
                    prop(Measurement::value).isEqualTo(0.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.MAX)
                }
                index(4).all {
                    isInstanceOf(DistributionMeasurementMetric::class).all {
                        prop(DistributionMeasurementMetric::value).isEqualTo(Double.NaN)
                        prop(DistributionMeasurementMetric::statistic).isEqualTo(Statistic.PERCENTILE)
                        prop(DistributionMeasurementMetric::observationPoint).isEqualTo(75.0)
                    }
                }
                index(5).all {
                    isInstanceOf(DistributionMeasurementMetric::class).all {
                        prop(DistributionMeasurementMetric::value).isEqualTo(Double.NaN)
                        prop(DistributionMeasurementMetric::statistic).isEqualTo(Statistic.PERCENTILE)
                        prop(DistributionMeasurementMetric::observationPoint).isEqualTo(99.0)
                    }
                }
            }
        }
    }

    @Test
    internal fun `should build the total snapshot and keep on cumulating`() = testDispatcherProvider.run {
        //given
        val id = Meter.Id("any-test", type = MeterType.DISTRIBUTION_SUMMARY, tags = mapOf("a" to "b", "c" to "d"))
        val summary = InMemoryCumulativeDistributionSummary(id, meterReporter, listOf(75.0, 99.0))
        summary.record(33.0)
        summary.record(68.9)
        summary.record(2.0)
        summary.record(12.0)
        summary.record(45.6)
        summary.record(77.0)

        // when
        val now = Instant.now()
        var result = summary.summarize(now)

        //then
        assertThat(result).isInstanceOf(MeterSnapshotImpl::class).all {
            prop(MeterSnapshotImpl::meterId).isEqualTo(
                Meter.Id(
                    "any-test",
                    type = MeterType.DISTRIBUTION_SUMMARY,
                    tags = mapOf("a" to "b", "c" to "d", "scope" to "campaign")
                )
            )
            prop(MeterSnapshotImpl::timestamp).isEqualTo(now)
            prop(MeterSnapshotImpl::measurements).transform { it.toList() }.all {
                hasSize(6)
                index(0).all {
                    prop(Measurement::value).isEqualTo(39.75)
                    prop(Measurement::statistic).isEqualTo(Statistic.MEAN)
                }
                index(1).all {
                    prop(Measurement::value).isEqualTo(6.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.COUNT)
                }
                index(2).all {
                    prop(Measurement::value).isEqualTo(238.5)
                    prop(Measurement::statistic).isEqualTo(Statistic.TOTAL)
                }
                index(3).all {
                    prop(Measurement::value).isEqualTo(77.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.MAX)
                }
                index(4).all {
                    isInstanceOf(DistributionMeasurementMetric::class).all {
                        prop(DistributionMeasurementMetric::value).isEqualTo(68.9)
                        prop(DistributionMeasurementMetric::statistic).isEqualTo(Statistic.PERCENTILE)
                        prop(DistributionMeasurementMetric::observationPoint).isEqualTo(75.0)
                    }
                }
                index(5).all {
                    isInstanceOf(DistributionMeasurementMetric::class).all {
                        prop(DistributionMeasurementMetric::value).isEqualTo(77.0)
                        prop(DistributionMeasurementMetric::statistic).isEqualTo(Statistic.PERCENTILE)
                        prop(DistributionMeasurementMetric::observationPoint).isEqualTo(99.0)
                    }
                }
            }
        }

        // when
        val values = listOf(68.9, 45.0, 32.0, 39.6)
        values.forEach(summary::record)
        result = summary.summarize(now)

        //then
        assertThat(result).isInstanceOf(MeterSnapshotImpl::class).all {
            prop(MeterSnapshotImpl::meterId).isEqualTo(
                Meter.Id(
                    "any-test",
                    type = MeterType.DISTRIBUTION_SUMMARY,
                    tags = mapOf("a" to "b", "c" to "d", "scope" to "campaign")
                )
            )
            prop(MeterSnapshotImpl::timestamp).isEqualTo(now)
            prop(MeterSnapshotImpl::measurements).transform { it.toList() }.all {
                hasSize(6)
                index(0).all {
                    prop(Measurement::value).isEqualTo(42.4)
                    prop(Measurement::statistic).isEqualTo(Statistic.MEAN)
                }
                index(1).all {
                    prop(Measurement::value).isEqualTo(10.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.COUNT)
                }
                index(2).all {
                    prop(Measurement::value).isEqualTo(424.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.TOTAL)
                }
                index(3).all {
                    prop(Measurement::value).isEqualTo(77.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.MAX)
                }
                index(4).all {
                    isInstanceOf(DistributionMeasurementMetric::class).all {
                        prop(DistributionMeasurementMetric::value).isEqualTo(68.9)
                        prop(DistributionMeasurementMetric::statistic).isEqualTo(Statistic.PERCENTILE)
                        prop(DistributionMeasurementMetric::observationPoint).isEqualTo(75.0)
                    }
                }
                index(5).all {
                    isInstanceOf(DistributionMeasurementMetric::class).all {
                        prop(DistributionMeasurementMetric::value).isEqualTo(77.0)
                        prop(DistributionMeasurementMetric::statistic).isEqualTo(Statistic.PERCENTILE)
                        prop(DistributionMeasurementMetric::observationPoint).isEqualTo(99.0)
                    }
                }
            }
        }
    }
}