/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.meters.inmemory.valued

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Statistic
import io.qalipsis.core.factory.meters.MeterSnapshotImpl
import io.qalipsis.core.factory.meters.inmemory.valued.catadioptre.currentCount
import io.qalipsis.core.factory.meters.inmemory.valued.catadioptre.measure
import io.qalipsis.core.reporter.MeterReporter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant
import java.util.concurrent.atomic.DoubleAdder

@WithMockk
internal class StepCounterTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var meterReporter: MeterReporter

    @Test
    internal fun `should increment the counter by 1 when increment is called without an argument`() {
        //given
        val id = mockk<Meter.Id>()
        val counter = StepCounter(id, meterReporter)

        //when
        counter.increment()

        //then
        assertThat(counter.currentCount()).isNotNull().all {
            prop(DoubleAdder::toDouble).isEqualTo(1.0)
        }
    }

    @Test
    internal fun `should increment the counter by the argument passed in when increment is called`() {
        //given
        val id = mockk<Meter.Id>()
        val counter = StepCounter(id, meterReporter)

        //when
        counter.increment(5.0)

        //then
        assertThat(counter.currentCount()).isNotNull().all {
            prop(DoubleAdder::toDouble).isEqualTo(5.0)
        }
    }

    @Test
    internal fun `should return the cumulative count`() {
        //given
        val id = mockk<Meter.Id>()
        val counter = StepCounter(id, meterReporter)
        assertThat(counter.count()).isEqualTo(0.0)
        counter.increment(5.0)
        counter.increment()
        counter.increment(2.0)
        assertThat(counter.currentCount()).isNotNull().all {
            prop(DoubleAdder::toDouble).isEqualTo(8.0)
        }

        //when
        var result = counter.count()

        //then
        assertThat(result).isEqualTo(8.0)

        // when
        counter.increment(3.0)
        result = counter.count()

        //then
        assertThat(result).isEqualTo(11.0)
    }

    @Test
    internal fun `should generate the correct measures and reset`() = testDispatcherProvider.run {
        //given
        val id = mockk<Meter.Id>()
        val counter = StepCounter(id, meterReporter)
        assertThat(counter.count()).isEqualTo(0.0)
        counter.increment(5.0)
        counter.increment()
        counter.increment(2.0)

        //when
        var result = counter.measure().toList()

        //then
        assertThat(result).isNotNull().all {
            hasSize(1)
            index(0).all {
                prop(Measurement::value).isEqualTo(8.0)
                prop(Measurement::statistic).isEqualTo(Statistic.COUNT)
            }
        }

        //when
        result = counter.measure().toList()

        //then
        assertThat(result).isNotNull().all {
            hasSize(1)
            index(0).all {
                prop(Measurement::value).isEqualTo(0.0)
                prop(Measurement::statistic).isEqualTo(Statistic.COUNT)
            }
        }
    }

    @Test
    internal fun `should build the snapshot and reset`() = testDispatcherProvider.run {
        //given
        val id = Meter.Id("any-test", type = MeterType.COUNTER, tags = mapOf("a" to "b", "c" to "d"))
        val counter = StepCounter(id, meterReporter)
        assertThat(counter.count()).isEqualTo(0.0)
        counter.increment(5.0)
        counter.increment()
        counter.increment(2.0)
        val now = Instant.now()

        //when
        var result = counter.snapshot(now)

        //then
        assertThat(result.toList()).all {
            hasSize(1)
            index(0).isInstanceOf(MeterSnapshotImpl::class).all {
                prop(MeterSnapshotImpl::meterId).isEqualTo(
                    Meter.Id(
                        "any-test",
                        type = MeterType.COUNTER,
                        tags = mapOf("a" to "b", "c" to "d", "scope" to "period")
                    )
                )
                prop(MeterSnapshotImpl::timestamp).isEqualTo(now)
                prop(MeterSnapshotImpl::measurements).transform { it.toList() }.all {
                    hasSize(1)
                    index(0).all {
                        prop(Measurement::value).isEqualTo(8.0)
                        prop(Measurement::statistic).isEqualTo(Statistic.COUNT)
                    }
                }
            }
        }

        //when
        result = counter.snapshot(now)

        //then
        assertThat(result.toList()).all {
            hasSize(1)
            index(0).isInstanceOf(MeterSnapshotImpl::class).all {
                prop(MeterSnapshotImpl::meterId).isEqualTo(
                    Meter.Id(
                        "any-test",
                        type = MeterType.COUNTER,
                        tags = mapOf("a" to "b", "c" to "d", "scope" to "period")
                    )
                )
                prop(MeterSnapshotImpl::timestamp).isEqualTo(now)
                prop(MeterSnapshotImpl::measurements).transform { it.toList() }.all {
                    hasSize(1)
                    index(0).all {
                        prop(Measurement::value).isEqualTo(0.0)
                        prop(Measurement::statistic).isEqualTo(Statistic.COUNT)
                    }
                }
            }
        }
    }
}