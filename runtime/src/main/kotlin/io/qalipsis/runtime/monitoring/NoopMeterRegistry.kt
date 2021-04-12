package io.qalipsis.runtime.monitoring

import io.micrometer.core.instrument.*
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.core.instrument.distribution.pause.PauseDetector
import io.micrometer.core.instrument.noop.*
import io.micronaut.context.annotation.Requires
import java.util.concurrent.TimeUnit
import java.util.function.ToDoubleFunction
import java.util.function.ToLongFunction
import javax.inject.Singleton

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
