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
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.Statistic
import io.qalipsis.core.factory.meters.catadioptre.counter
import io.qalipsis.core.factory.meters.catadioptre.total
import io.qalipsis.core.reporter.MeterReporter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class TimerImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var meterReporter: MeterReporter

    @Test
    internal fun `should increment the counter by 1 when record is called`() {
        //given
        val id = mockk<Meter.Id>()
        val timer = TimerImpl(id, meterReporter, emptyList())
        timer.record(Duration.of(2560, ChronoUnit.MILLIS))
        timer.record(Duration.of(2005660, ChronoUnit.MILLIS))
        timer.record(Duration.of(600311110, ChronoUnit.MICROS))
        timer.record(Duration.of(6, ChronoUnit.MINUTES))
        timer.record(Duration.of(700, ChronoUnit.SECONDS))

        //when
        val result = timer.count()

        //then
        assertThat(result).isEqualTo(5)
    }

    @Test
    internal fun `should return the total of the collected observations`() {
        //given
        val id = mockk<Meter.Id>()
        val timer = TimerImpl(id, meterReporter, emptyList())
        timer.record(Duration.of(2560, ChronoUnit.MILLIS))
        timer.record(Duration.of(2005660, ChronoUnit.MILLIS))
        timer.record(Duration.of(600311110, ChronoUnit.MICROS))
        timer.record(Duration.of(6, ChronoUnit.MINUTES))
        timer.record(Duration.of(700, ChronoUnit.SECONDS))

        //when
        val timerInMillis = timer.totalTime(TimeUnit.MILLISECONDS)
        val timerInNanos = timer.totalTime(TimeUnit.NANOSECONDS)
        val timerInMicros = timer.totalTime(TimeUnit.MICROSECONDS)
        val timerInSeconds = timer.totalTime(TimeUnit.SECONDS)
        val timerInMinutes = timer.totalTime(TimeUnit.MINUTES)
        val timerInHours = timer.totalTime(TimeUnit.HOURS)
        val timerInDays = timer.totalTime(TimeUnit.DAYS)

        //then
        assertThat(timerInMillis).isEqualTo(3668531.11)
        assertThat(timerInNanos).isEqualTo(3.66853111E12)
        assertThat(timerInMicros).isEqualTo(3.66853111E9)
        assertThat(timerInSeconds).isEqualTo(3668.53111)
        assertThat(timerInMinutes).isEqualTo(61.142185166666664)
        assertThat(timerInHours).isEqualTo(1.0190364194444443)
        assertThat(timerInDays).isEqualTo(0.04245985081018518)
    }

    @Test
    internal fun `should return the max of the collected observations`() {
        //given
        val id = mockk<Meter.Id>()
        val timer = TimerImpl(id, meterReporter, emptyList())
        timer.record(Duration.of(2560, ChronoUnit.MILLIS))
        timer.record(Duration.of(2005660, ChronoUnit.MILLIS))
        timer.record(Duration.of(600311110, ChronoUnit.MICROS))
        timer.record(Duration.of(6, ChronoUnit.MINUTES))
        timer.record(Duration.of(700, ChronoUnit.SECONDS))

        //when
        val maxInSeconds = timer.max(TimeUnit.SECONDS)
        val maxInMillis = timer.max(TimeUnit.MILLISECONDS)

        //then
        assertThat(maxInSeconds).isEqualTo(2005.66)
        assertThat(maxInMillis).isEqualTo(2005660.0)
    }

    @Test
    internal fun `should return the mean of the collected observations`() {
        //given
        val id = mockk<Meter.Id>()
        val timer = TimerImpl(id, meterReporter, emptyList())
        timer.record(Duration.of(2560, ChronoUnit.MILLIS))
        timer.record(Duration.of(2005660, ChronoUnit.MILLIS))
        timer.record(Duration.of(600311110, ChronoUnit.MICROS))
        timer.record(Duration.of(6, ChronoUnit.MINUTES))
        timer.record(Duration.of(700, ChronoUnit.SECONDS))

        //when
        val meanInNanos = timer.mean(TimeUnit.NANOSECONDS)
        val meanInMicros = timer.mean(TimeUnit.MICROSECONDS)

        //then
        assertThat(meanInNanos).isEqualTo(7.33706222E11)
        assertThat(meanInMicros).isEqualTo(7.33706222E8)
    }

    @Test
    internal fun `should record collected observations`() {
        //given
        val id = mockk<Meter.Id>()
        val timer = TimerImpl(id, meterReporter, listOf(25.0, 50.0))

        // when
        timer.record(Duration.of(2560, ChronoUnit.MILLIS))
        timer.record(Duration.of(2560, ChronoUnit.MILLIS))
        timer.record(Duration.of(2005660, ChronoUnit.MILLIS))
        timer.record(Duration.of(600311110, ChronoUnit.MICROS))
        timer.record(Duration.of(600311110, ChronoUnit.MICROS))
        timer.record(Duration.of(6, ChronoUnit.MINUTES))
        timer.record(Duration.of(700, ChronoUnit.SECONDS))
        timer.record(Duration.of(700, ChronoUnit.SECONDS))

        //then
        assertThat(timer.counter()).transform { it.toDouble() }.isEqualTo(8.0)
        assertThat(timer.total()).transform { it.toDouble() }.isEqualTo(4971402220.0)
    }

    @Test
    internal fun `should return the percentile of the collected observations`() {
        //given
        val id = mockk<Meter.Id>()
        val timer = TimerImpl(id, meterReporter, listOf(25.0))
        timer.record(Duration.of(2560, ChronoUnit.MILLIS))
        timer.record(Duration.of(2560, ChronoUnit.MILLIS))
        timer.record(Duration.of(2005660, ChronoUnit.MILLIS))
        timer.record(Duration.of(600311110, ChronoUnit.MICROS))
        timer.record(Duration.of(600311110, ChronoUnit.MICROS))
        timer.record(Duration.of(6, ChronoUnit.MINUTES))
        timer.record(Duration.of(700, ChronoUnit.SECONDS))
        timer.record(Duration.of(700, ChronoUnit.SECONDS))

        //when
        val fiftiethPercentileInMillis = timer.percentile(50.0, TimeUnit.MILLISECONDS)

        //then
        assertThat(fiftiethPercentileInMillis).isBetween(600311.10, 600311.12)
    }

    @Test
    internal fun `should not return the percentile of the collected observations if no percentile value was supplied`() {
        //given
        val id = mockk<Meter.Id>()
        val timer = TimerImpl(id, meterReporter, emptyList())
        timer.record(Duration.of(2560, ChronoUnit.MILLIS))
        timer.record(Duration.of(2560, ChronoUnit.MILLIS))
        timer.record(Duration.of(2005660, ChronoUnit.MILLIS))
        timer.record(Duration.of(600311110, ChronoUnit.MICROS))
        timer.record(Duration.of(600311110, ChronoUnit.MICROS))
        timer.record(Duration.of(6, ChronoUnit.MINUTES))
        timer.record(Duration.of(700, ChronoUnit.SECONDS))
        timer.record(Duration.of(700, ChronoUnit.SECONDS))

        //when
        val fiftiethPercentileInMillis = timer.percentile(50.0, TimeUnit.MILLISECONDS)

        //then
        assertThat(fiftiethPercentileInMillis).isEqualTo(0.0)
    }


    @Test
    internal fun `should generate the correct measures`() = testDispatcherProvider.run {
        //given
        val id = mockk<Meter.Id>()
        val timer = TimerImpl(id, meterReporter, listOf(25.0, 99.0))
        timer.record(Duration.of(2560, ChronoUnit.MILLIS))
        timer.record(Duration.of(2005660, ChronoUnit.MILLIS))
        timer.record(Duration.of(600311110, ChronoUnit.MICROS))
        timer.record(Duration.of(6, ChronoUnit.MINUTES))
        timer.record(Duration.of(700, ChronoUnit.SECONDS))

        // when
        val result = timer.measure().toList()

        //then
        assertThat(result).isNotNull().all {
            hasSize(6)
            index(0).all {
                prop(Measurement::value).isEqualTo(5.0)
                prop(Measurement::statistic).isEqualTo(Statistic.COUNT)
            }
            index(1).all {
                prop(Measurement::value).isEqualTo(3.66853111E9)
                prop(Measurement::statistic).isEqualTo(Statistic.TOTAL_TIME)
            }
            index(2).all {
                prop(Measurement::value).isEqualTo(2.00566E9)
                prop(Measurement::statistic).isEqualTo(Statistic.MAX)
            }
            index(3).all {
                prop(Measurement::value).isEqualTo(7.33706222E8)
                prop(Measurement::statistic).isEqualTo(Statistic.MEAN)
            }
            index(4).all {
                isInstanceOf(DistributionMeasurementMetric::class).all {
                    prop(DistributionMeasurementMetric::value).isEqualTo(3.6E8)
                    prop(DistributionMeasurementMetric::statistic).isEqualTo(Statistic.PERCENTILE)
                    prop(DistributionMeasurementMetric::observationPoint).isEqualTo(25.0)
                }
            }
            index(5).all {
                isInstanceOf(DistributionMeasurementMetric::class).all {
                    prop(DistributionMeasurementMetric::value).isEqualTo(2.00566E9)
                    prop(DistributionMeasurementMetric::statistic).isEqualTo(Statistic.PERCENTILE)
                    prop(DistributionMeasurementMetric::observationPoint).isEqualTo(99.0)
                }
            }
        }
    }

    @Test
    internal fun `should build snapshot`() = testDispatcherProvider.run {
        //given
        val id = mockk<Meter.Id>()
        val timer = TimerImpl(id, meterReporter, listOf(25.0, 99.0))
        timer.record(Duration.of(2560, ChronoUnit.MILLIS))
        timer.record(Duration.of(2005660, ChronoUnit.MILLIS))
        timer.record(Duration.of(600311110, ChronoUnit.MICROS))
        timer.record(Duration.of(6, ChronoUnit.MINUTES))
        timer.record(Duration.of(700, ChronoUnit.SECONDS))

        // when
        val now = Instant.now()
        val result = timer.buildSnapshot(now)

        //then
        assertThat(result).isInstanceOf(MeterSnapshotImpl::class).all {
            prop(MeterSnapshotImpl<*>::meter).isEqualTo(timer)
            prop(MeterSnapshotImpl<*>::timestamp).isEqualTo(now)
            prop(MeterSnapshotImpl<*>::measurements).transform { it.toList() }.all {
                hasSize(6)
                index(0).all {
                    prop(Measurement::value).isEqualTo(5.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.COUNT)
                }
                index(1).all {
                    prop(Measurement::value).isEqualTo(3.66853111E9)
                    prop(Measurement::statistic).isEqualTo(Statistic.TOTAL_TIME)
                }
                index(2).all {
                    prop(Measurement::value).isEqualTo(2.00566E9)
                    prop(Measurement::statistic).isEqualTo(Statistic.MAX)
                }
                index(3).all {
                    prop(Measurement::value).isEqualTo(7.33706222E8)
                    prop(Measurement::statistic).isEqualTo(Statistic.MEAN)
                }
                index(4).all {
                    isInstanceOf(DistributionMeasurementMetric::class).all {
                        prop(DistributionMeasurementMetric::value).isEqualTo(3.6E8)
                        prop(DistributionMeasurementMetric::statistic).isEqualTo(Statistic.PERCENTILE)
                        prop(DistributionMeasurementMetric::observationPoint).isEqualTo(25.0)
                    }
                }
                index(5).all {
                    isInstanceOf(DistributionMeasurementMetric::class).all {
                        prop(DistributionMeasurementMetric::value).isEqualTo(2.00566E9)
                        prop(DistributionMeasurementMetric::statistic).isEqualTo(Statistic.PERCENTILE)
                        prop(DistributionMeasurementMetric::observationPoint).isEqualTo(99.0)
                    }
                }
            }
        }
    }
}