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
import io.qalipsis.core.factory.meters.inmemory.valued.catadioptre.benchmark
import io.qalipsis.core.factory.meters.inmemory.valued.catadioptre.measure
import io.qalipsis.core.factory.meters.inmemory.valued.catadioptre.total
import io.qalipsis.core.reporter.MeterReporter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant
import java.util.concurrent.atomic.DoubleAdder

@WithMockk
internal class StepRateTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var meterReporter: MeterReporter

    @Test
    internal fun `should decrement both the total and the benchmark`() {
        // given
        val id = mockk<Meter.Id>()
        val rate = StepRate(id, meterReporter)
        rate.incrementTotal(14.0)
        rate.incrementBenchmark(7.0)

        // when
        rate.decrementTotal(2.0)
        rate.decrementTotal()
        rate.decrementBenchmark(5.0)
        rate.decrementBenchmark()

        // then
        assertThat(rate.benchmark()).isNotNull().all {
            prop(DoubleAdder::toDouble).isEqualTo(1.0)
        }
        assertThat(rate.total()).isNotNull().all {
            prop(DoubleAdder::toDouble).isEqualTo(11.0)
        }
    }

    @Test
    internal fun `should increment both the total and the benchmark`() {
        // given
        val id = mockk<Meter.Id>()
        val rate = StepRate(id, meterReporter)

        // when
        rate.incrementTotal(9.0)
        rate.incrementTotal(1.0)
        rate.incrementBenchmark(7.0)
        rate.incrementBenchmark(5.0)

        // then
        assertThat(rate.benchmark()).isNotNull().all {
            prop(DoubleAdder::toDouble).isEqualTo(12.0)
        }
        assertThat(rate.total()).isNotNull().all {
            prop(DoubleAdder::toDouble).isEqualTo(10.0)
        }
    }

    @Test
    internal fun `should return the rate of tracked meters when an argument is passed into the increment and decrement functions`() {
        // given
        val id = mockk<Meter.Id>()
        val rate = StepRate(id, meterReporter)

        // when
        rate.incrementTotal(19.0)
        rate.decrementTotal(3.0)
        rate.incrementBenchmark(7.0)
        rate.decrementBenchmark(3.0)

        // then
        assertThat(rate.current()).isEqualTo(0.25)
        assertThat(rate.benchmark()).isNotNull().all {
            prop(DoubleAdder::toDouble).isEqualTo(4.0)
        }
        assertThat(rate.total()).isNotNull().all {
            prop(DoubleAdder::toDouble).isEqualTo(16.0)
        }
    }

    @Test
    internal fun `should return the rate of tracked meters when the default arguments is passed into the increment and decrement functions`() {
        // given
        val id = mockk<Meter.Id>()

        val rate = StepRate(id, meterReporter)

        // when
        rate.incrementTotal()
        rate.incrementTotal()
        rate.incrementTotal()
        rate.decrementTotal()
        rate.incrementBenchmark()
        rate.incrementBenchmark()
        rate.decrementBenchmark()

        // then
        assertThat(rate.current()).isEqualTo(0.5)
        assertThat(rate.benchmark()).isNotNull().all {
            prop(DoubleAdder::toDouble).isEqualTo(1.0)
        }
        assertThat(rate.total()).isNotNull().all {
            prop(DoubleAdder::toDouble).isEqualTo(2.0)
        }
    }

    @Test
    internal fun `should return the rate for zero value for the total`() {
        // given
        val id = mockk<Meter.Id>()

        val rate = StepRate(id, meterReporter)

        // when
        rate.incrementBenchmark()

        // then
        assertThat(rate.current()).isEqualTo(0.0)
    }

    @Test
    internal fun `should return the rate for zero value of the benchmark`() {
        // given
        val id = mockk<Meter.Id>()

        val rate = StepRate(id, meterReporter)

        // when
        rate.incrementTotal()

        // then
        assertThat(rate.current()).isEqualTo(0.0)
    }


    @Test
    internal fun `should generate the correct measures and reset`() = testDispatcherProvider.run {
        //given
        val id = mockk<Meter.Id>()
        val rate = StepRate(id, meterReporter)
        assertThat(rate.current()).isEqualTo(0.0)
        assertThat(rate.total().sum()).isEqualTo(0.0)
        assertThat(rate.benchmark().sum()).isEqualTo(0.0)
        rate.incrementTotal(10.0)
        rate.incrementTotal(4.0)
        rate.incrementTotal(3.0)
        rate.decrementTotal(2.0)
        rate.incrementBenchmark(7.0)
        rate.decrementBenchmark(1.0)

        //when
        var result = rate.measure().toList()

        //then
        assertThat(result).isNotNull().all {
            hasSize(1)
            index(0).all {
                prop(Measurement::value).isEqualTo(0.4)
                prop(Measurement::statistic).isEqualTo(Statistic.VALUE)
            }
        }

        //when
        result = rate.measure().toList()

        //then
        assertThat(result).isNotNull().all {
            hasSize(1)
            index(0).all {
                prop(Measurement::value).isEqualTo(0.0)
                prop(Measurement::statistic).isEqualTo(Statistic.VALUE)
            }
        }
    }

    @Test
    internal fun `should build the snapshot and reset`() = testDispatcherProvider.run {
        //given
        val id = Meter.Id("any-test", type = MeterType.RATE, tags = mapOf("a" to "b", "c" to "d"))
        val rate = StepRate(id, meterReporter)
        assertThat(rate.current()).isEqualTo(0.0)
        assertThat(rate.total().sum()).isEqualTo(0.0)
        assertThat(rate.benchmark().sum()).isEqualTo(0.0)
        rate.incrementTotal(10.0)
        rate.incrementTotal(4.0)
        rate.incrementTotal(3.0)
        rate.decrementTotal(2.0)
        rate.incrementBenchmark(7.0)
        rate.decrementBenchmark(1.0)
        val now = Instant.now()

        //when
        var result = rate.snapshot(now)

        //then
        assertThat(result.toList()).all {
            hasSize(1)
            index(0).isInstanceOf(MeterSnapshotImpl::class).all {
                prop(MeterSnapshotImpl::meterId).isEqualTo(
                    Meter.Id(
                        "any-test",
                        type = MeterType.RATE,
                        tags = mapOf("a" to "b", "c" to "d", "scope" to "period")
                    )
                )
                prop(MeterSnapshotImpl::timestamp).isEqualTo(now)
                prop(MeterSnapshotImpl::measurements).transform { it.toList() }.all {
                    hasSize(1)
                    index(0).all {
                        prop(Measurement::value).isEqualTo(0.4)
                        prop(Measurement::statistic).isEqualTo(Statistic.VALUE)
                    }
                }
            }
        }

        //when
        result = rate.snapshot(now)

        //then
        assertThat(result.toList()).all {
            hasSize(1)
            index(0).isInstanceOf(MeterSnapshotImpl::class).all {
                prop(MeterSnapshotImpl::meterId).isEqualTo(
                    Meter.Id(
                        "any-test",
                        type = MeterType.RATE,
                        tags = mapOf("a" to "b", "c" to "d", "scope" to "period")
                    )
                )
                prop(MeterSnapshotImpl::timestamp).isEqualTo(now)
                prop(MeterSnapshotImpl::measurements).transform { it.toList() }.all {
                    hasSize(1)
                    index(0).all {
                        prop(Measurement::value).isEqualTo(0.0)
                        prop(Measurement::statistic).isEqualTo(Statistic.VALUE)
                    }
                }
            }
        }
    }

}