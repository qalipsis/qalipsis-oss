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
import assertk.assertions.prop
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.Statistic
import io.qalipsis.core.reporter.MeterReporter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import java.time.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class GaugeImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var meterReporter: MeterReporter

    @Test
    internal fun `should increment the tracked value of a measurement`() {
        // given
        val id = mockk<Meter.Id>()
        val gauge = GaugeImpl(id, meterReporter)
        gauge.increment()
        gauge.increment()
        gauge.increment(7.0)

        // when
        val result = gauge.value()

        // then
        assertThat(result).isEqualTo(9.0)
    }

    @Test
    internal fun `should decrement the tracked value of a measurement`() {
        // given
        val id = mockk<Meter.Id>()
        val gauge = GaugeImpl(id, meterReporter)
        gauge.increment(13.0)
        gauge.decrement()
        gauge.decrement(7.0)

        // when
        val result = gauge.value()

        // then
        assertThat(result).isEqualTo(5.0)
    }

    @Test
    internal fun `should increment and decrement the tracked value of a measurement`() {
        // given
        val id = mockk<Meter.Id>()
        val gauge = GaugeImpl(id, meterReporter)
        gauge.increment(13.0)
        gauge.decrement(7.0)
        gauge.decrement()
        gauge.increment(7.0)
        gauge.decrement(2.0)

        // when
        val result = gauge.value()

        // then
        assertThat(result).isEqualTo(10.0)
    }


    @Test
    internal fun measure() = testDispatcherProvider.run {
        // given
        val id = mockk<Meter.Id>()
        val gauge = GaugeImpl(id, meterReporter)
        gauge.increment(13.0)
        gauge.decrement(7.0)
        gauge.decrement()
        gauge.increment(7.0)
        gauge.decrement(2.0)

        // when
        val result = gauge.measure()

        // then
        assertThat(result.size).isEqualTo(1)
        assertThat(result.containsAll(listOf(MeasurementMetric(10.0, Statistic.COUNT))))
    }

    @Test
    internal fun buildSnapshot() = testDispatcherProvider.run {
        // given
        val id = mockk<Meter.Id>()
        val gauge = GaugeImpl(id, meterReporter)
        gauge.increment(2.0)
        gauge.increment(5.0)
        gauge.decrement()
        val now = Instant.now()

        // when
        val result = gauge.buildSnapshot(now)

        // then
        assertThat(result).isInstanceOf(MeterSnapshotImpl::class).all {
            prop(MeterSnapshotImpl<*>::meter).isEqualTo(gauge)
            prop(MeterSnapshotImpl<*>::timestamp).isEqualTo(now)
            prop(MeterSnapshotImpl<*>::measurements).transform { it.toList() }.all {
                hasSize(1)
                index(0).all {
                    prop(Measurement::value).isEqualTo(6.0)
                    prop(Measurement::statistic).isEqualTo(Statistic.VALUE)
                }
            }
        }
    }

}