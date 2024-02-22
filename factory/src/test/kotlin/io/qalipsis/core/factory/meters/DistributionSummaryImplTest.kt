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

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.Statistic
import io.qalipsis.core.factory.meters.catadioptre.observationCount
import io.qalipsis.core.factory.meters.catadioptre.total
import io.qalipsis.core.reporter.MeterReporter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import java.time.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@MicronautTest
@WithMockk
class DistributionSummaryImplTest {
    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var meterReporter: MeterReporter

    @Test
    fun `should increment the counter by 1 when record is called`() = testDispatcherProvider.run {
            //given
            val id = mockk<Meter.Id>()
            val summary = DistributionSummaryImpl(id, meterReporter, listOf())
            summary.record(5.0)
            summary.record(6.0)
            summary.record(2.0)

            //when
            val result = summary.count()

            //then
            assertThat(result).isEqualTo(3)
        }

    @Test
    fun `should return the total of the collected observations`() = testDispatcherProvider.run {
            //given
            val id = mockk<Meter.Id>()
            val summary = DistributionSummaryImpl(id, meterReporter, listOf())
            summary.record(5.0)
            summary.record(6.0)
            summary.record(2.0)

            //when
            val result = summary.totalAmount()

            //then
            assertThat(result).isEqualTo(13.0)
        }

    @Test
    fun `should return the max of the collected observations`() = testDispatcherProvider.run {
            //given
            val id = mockk<Meter.Id>()
            val summary = DistributionSummaryImpl(id, meterReporter, listOf())
            summary.record(5.0)
            summary.record(6.0)
            summary.record(2.0)

            //when
            val result = summary.max()

            //then
            assertThat(result).isEqualTo(6.0)
        }

    @Test
    fun `should return the mean of the collected observations`() = testDispatcherProvider.run {
            //given
            val id = mockk<Meter.Id>()
            val summary = DistributionSummaryImpl(id, meterReporter, listOf())
            summary.record(5.0)
            summary.record(6.0)
            summary.record(2.0)

            //when
            val result = summary.mean()

            //then
            assertThat(result).isEqualTo(4.334)
        }

    @Test
    fun `should record collected observations`() = testDispatcherProvider.run {
            //given
            val id = mockk<Meter.Id>()
            val summary = DistributionSummaryImpl(id, meterReporter, listOf())

            // when
            summary.record(5.0)
            summary.record(11.2)
            summary.record(11.2)

            //then
            assertThat(summary.observationCount()).transform { it.toDouble() }.isEqualTo(3.0)
            assertThat(summary.total()).transform { it.toDouble() }.isEqualTo(27.4)
        }

    @Test
    fun `should return the observationPoint of the collected observations`() = testDispatcherProvider.run {
            //given
            val id = mockk<Meter.Id>()
            val summary = DistributionSummaryImpl(id, meterReporter, listOf())
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
    fun measure() = testDispatcherProvider.run {
            //given
            val id = mockk<Meter.Id>()
            val summary = DistributionSummaryImpl(id, meterReporter, listOf(75.0, 99.0))
            summary.record(33.0)
            summary.record(68.9)
            summary.record(2.0)
            summary.record(12.0)
            summary.record(45.6)
            summary.record(77.0)

            // when
            val result = summary.measure().toList()

            //then
            assertThat(result).isNotNull().all {
                hasSize(6)
                index(0).all {
                    prop(Measurement::value).isEqualTo(6.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.COUNT)
                }
                index(1).all {
                    prop(Measurement::value).isEqualTo(238.5)
                    prop(Measurement::statistic).isEqualTo(Statistic.TOTAL)
                }
                index(2).all {
                    prop(Measurement::value).isEqualTo(77.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.MAX)
                }
                index(3).all {
                    prop(Measurement::value).isEqualTo(39.75)
                    prop(Measurement::statistic).isEqualTo(Statistic.MEAN)
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

    @Test
    fun buildSnapshot() = testDispatcherProvider.run {
            //given
            val id = mockk<Meter.Id>()
            val summary = DistributionSummaryImpl(id, meterReporter, listOf(75.0, 99.0))
            summary.record(33.0)
            summary.record(68.9)
            summary.record(2.0)
            summary.record(12.0)
            summary.record(45.6)
            summary.record(77.0)

            // when
            val now = Instant.now()
            val result = summary.buildSnapshot(now)

            //then
            assertThat(result).isInstanceOf(MeterSnapshotImpl::class).all {
                prop(MeterSnapshotImpl<*>::meter).isEqualTo(summary)
                prop(MeterSnapshotImpl<*>::timestamp).isEqualTo(now)
                prop(MeterSnapshotImpl<*>::measurements).transform { it.toList() }.all {
                    hasSize(6)
                    index(0).all {
                        prop(Measurement::value).isEqualTo(6.0)
                        prop(Measurement::statistic).isEqualTo(Statistic.COUNT)
                    }
                    index(1).all {
                        prop(Measurement::value).isEqualTo(238.5)
                        prop(Measurement::statistic).isEqualTo(Statistic.TOTAL)
                    }
                    index(2).all {
                        prop(Measurement::value).isEqualTo(77.0)
                        prop(Measurement::statistic).isEqualTo(Statistic.MAX)
                    }
                    index(3).all {
                        prop(Measurement::value).isEqualTo(39.75)
                        prop(Measurement::statistic).isEqualTo(Statistic.MEAN)
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