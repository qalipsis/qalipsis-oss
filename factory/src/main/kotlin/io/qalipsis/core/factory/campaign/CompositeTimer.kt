package io.qalipsis.core.factory.campaign

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.distribution.HistogramSnapshot
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.function.Supplier

/**
 * Composite class to encapsulate the [Timer]s at the scenario and campaign
 * level in order to update both at the same time.
 *
 * This [Timer] should be seen as the [scenarioLevelTimer] by the calling application.
 * The meter registries are the only one aware of the existence of the [campaignLevelTimer]
 * and will ask for its publication when required.
 *
 * This instance of [Timer] is not known by the instances of [io.micrometer.core.instrument.MeterRegistry].
 *
 * @author Joël Valère
 */
internal data class CompositeTimer(
    private val clock: Clock,
    private val scenarioLevelTimer: Timer,
    private val campaignLevelTimer: Timer
) : Timer {

    override fun getId(): Meter.Id = scenarioLevelTimer.id

    override fun takeSnapshot(): HistogramSnapshot = scenarioLevelTimer.takeSnapshot()

    override fun record(amount: Long, unit: TimeUnit) {
        scenarioLevelTimer.record(amount, unit)
        campaignLevelTimer.record(amount, unit)
    }

    override fun record(duration: Duration) {
        scenarioLevelTimer.record(duration)
        campaignLevelTimer.record(duration)
    }

    override fun <T : Any?> record(f: Supplier<T>): T? {
        val s: Long = clock.monotonicTime()
        return try {
            f.get()
        } finally {
            val e: Long = clock.monotonicTime()
            record(e - s, NANOSECONDS)
        }
    }

    override fun record(f: Runnable) {
        val s = clock.monotonicTime()
        try {
            f.run()
        } finally {
            val e = clock.monotonicTime()
            record(e - s, NANOSECONDS)
        }
    }

    override fun <T : Any?> recordCallable(f: Callable<T>): T? {
        val s = clock.monotonicTime()
        return try {
            f.call()
        } finally {
            val e = clock.monotonicTime()
            record(e - s, NANOSECONDS)
        }
    }

    override fun count() = scenarioLevelTimer.count()

    override fun totalTime(unit: TimeUnit) = scenarioLevelTimer.totalTime(unit)

    override fun max(unit: TimeUnit) = scenarioLevelTimer.max(unit)

    override fun baseTimeUnit(): TimeUnit = scenarioLevelTimer.baseTimeUnit()
}