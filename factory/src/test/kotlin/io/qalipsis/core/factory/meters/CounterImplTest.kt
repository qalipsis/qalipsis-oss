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
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.Statistic
import io.qalipsis.core.factory.meters.catadioptre.current
import io.qalipsis.core.reporter.MeterReporter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import java.time.Instant
import java.util.concurrent.atomic.DoubleAdder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class CounterImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var meterReporter: MeterReporter

    @Test
    internal fun `should increment the counter by 1 when increment is called without an argument`() {
            //given
            val id = mockk<Meter.Id>()
            val counter = CounterImpl(id, meterReporter)

            //when
            counter.increment()

            //then
            assertThat(counter.current()).isNotNull().all {
                prop(DoubleAdder::toDouble).isEqualTo(1.0)
            }
        }

    @Test
    internal fun `should increment the counter by the argument passed in when increment is called`() {
            //given
            val id = mockk<Meter.Id>()
            val counter = CounterImpl(id, meterReporter)

            //when
            counter.increment(5.0)

            //then
            assertThat(counter.current()).isNotNull().all {
                prop(DoubleAdder::toDouble).isEqualTo(5.0)
            }
        }

    @Test
    internal fun `should return the cumulative count and reset value to zero`() {
            //given
            val id = mockk<Meter.Id>()
            val counter = CounterImpl(id, meterReporter)
            assertThat(counter.count()).isEqualTo(0.0)
            counter.increment(5.0)
            counter.increment()
            counter.increment(2.0)
            assertThat(counter.current()).isNotNull().all {
                prop(DoubleAdder::toDouble).isEqualTo(8.0)
            }

            //when
            val result = counter.count()

            //then
            assertThat(result).isEqualTo(8.0)
            assertThat(counter.current()).isNotNull().all {
                prop(DoubleAdder::toDouble).isEqualTo(0.0)
            }
        }

    @Test
    internal fun measure() = testDispatcherProvider.run {
            //given
            val id = mockk<Meter.Id>()
            val counter = CounterImpl(id, meterReporter)
            assertThat(counter.count()).isEqualTo(0.0)
            counter.increment(5.0)
            counter.increment()
            counter.increment(2.0)

            //when
            val result = counter.measure().toList()

            //then
            assertThat(result).isNotNull().all {
                hasSize(1)
                index(0).all {
                    prop(Measurement::value).isEqualTo(8.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.COUNT)
                }
            }
        }

    @Test
    internal fun `should build the snapshot`() = testDispatcherProvider.run {
            //given
            val id = mockk<Meter.Id>()
            val counter = CounterImpl(id, meterReporter)
            assertThat(counter.count()).isEqualTo(0.0)
            counter.increment(5.0)
            counter.increment()
            counter.increment(2.0)
            val now = Instant.now()

            //when
            val result = counter.buildSnapshot(now)

            //then
            assertThat(result).isInstanceOf(MeterSnapshotImpl::class).all {
                prop(MeterSnapshotImpl<*>::meter).isEqualTo(counter)
                prop(MeterSnapshotImpl<*>::timestamp).isEqualTo(now)
                prop(MeterSnapshotImpl<*>::measurements).transform { it.toList() }.all {
                    hasSize(1)
                    index(0).all {
                        prop(Measurement::value).isEqualTo(8.0)
                        prop(Measurement::statistic).isEqualTo(Statistic.COUNT)
                    }
                }
            }
        }
}