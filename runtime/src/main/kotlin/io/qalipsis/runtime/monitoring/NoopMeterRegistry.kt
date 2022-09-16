/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.runtime.monitoring

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Measurement
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.core.instrument.distribution.pause.PauseDetector
import io.micrometer.core.instrument.noop.NoopCounter
import io.micrometer.core.instrument.noop.NoopDistributionSummary
import io.micrometer.core.instrument.noop.NoopFunctionCounter
import io.micrometer.core.instrument.noop.NoopFunctionTimer
import io.micrometer.core.instrument.noop.NoopGauge
import io.micrometer.core.instrument.noop.NoopLongTaskTimer
import io.micrometer.core.instrument.noop.NoopTimer
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import java.util.concurrent.TimeUnit
import java.util.function.ToDoubleFunction
import java.util.function.ToLongFunction

/**
 * No-op meter registry used for injection purpose when the monitoring is disabled.
 *
 * This implementation provides the same instances of each meter types to all the caller.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(missingBeans = [MeterRegistry::class])
internal class NoopMeterRegistry : MeterRegistry(Clock.SYSTEM) {

    private val gauge = NoopGauge(Meter.Id("noop", Tags.empty(), "", "", Meter.Type.GAUGE))
    private val timer = NoopTimer(Meter.Id("noop", Tags.empty(), "", "", Meter.Type.TIMER))
    private val functionTimer = NoopFunctionTimer(Meter.Id("noop", Tags.empty(), "", "", Meter.Type.TIMER))
    private val functionCounter = NoopFunctionCounter(Meter.Id("noop", Tags.empty(), "", "", Meter.Type.COUNTER))
    private val distributionSummary =
        NoopDistributionSummary(Meter.Id("noop", Tags.empty(), "", "", Meter.Type.DISTRIBUTION_SUMMARY))
    private val counter = NoopCounter(Meter.Id("noop", Tags.empty(), "", "", Meter.Type.COUNTER))
    private val longTaskTimer = NoopLongTaskTimer(Meter.Id("noop", Tags.empty(), "", "", Meter.Type.LONG_TASK_TIMER))
    private val distributionStatisticConfig = DistributionStatisticConfig()

    override fun <T : Any?> newGauge(id: Meter.Id, obj: T?, valueFunction: ToDoubleFunction<T>) = gauge

    override fun newCounter(id: Meter.Id) = counter

    override fun newTimer(id: Meter.Id, distributionStatisticConfig: DistributionStatisticConfig,
                          pauseDetector: PauseDetector) = timer

    override fun newDistributionSummary(id: Meter.Id, distributionStatisticConfig: DistributionStatisticConfig,
                                        scale: Double) = distributionSummary

    override fun newMeter(id: Meter.Id, type: Meter.Type, measurements: MutableIterable<Measurement>): Meter {
        return when (type) {
            Meter.Type.COUNTER -> counter
            Meter.Type.GAUGE -> gauge
            Meter.Type.LONG_TASK_TIMER -> longTaskTimer
            Meter.Type.TIMER -> timer
            Meter.Type.DISTRIBUTION_SUMMARY -> distributionSummary
            Meter.Type.OTHER -> throw IllegalArgumentException("Unexpected meter type")
        }
    }

    override fun <T : Any?> newFunctionTimer(id: Meter.Id, obj: T, countFunction: ToLongFunction<T>,
                                             totalTimeFunction: ToDoubleFunction<T>,
                                             totalTimeFunctionUnit: TimeUnit) = functionTimer

    override fun <T : Any?> newFunctionCounter(id: Meter.Id, obj: T,
                                               countFunction: ToDoubleFunction<T>) = functionCounter

    override fun getBaseTimeUnit() = TimeUnit.MICROSECONDS

    override fun defaultHistogramConfig() = distributionStatisticConfig

}
