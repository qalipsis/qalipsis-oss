package io.evolue.plugins.netty.monitoring

import io.evolue.api.annotations.PluginComponent
import io.evolue.api.context.StepContext
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@PluginComponent
internal class MetricsRecorderImpl(
    private val meterRegistry: MeterRegistry
) : MetricsRecorder {

    private val timers = ConcurrentHashMap<MeterId, Timer>()

    private val counters = ConcurrentHashMap<MeterId, Counter>()

    override fun recordSuccessfulConnectionTime(stepContext: StepContext<*, *>, durationNs: Long) {
        val meterId = MeterId("connection-time", stepContext.toMetersTags().and("status", "SUCCESS"))
        timers.computeIfAbsent(meterId) { key -> meterRegistry.timer(key.name, key.tags) }
            .record(durationNs, TimeUnit.NANOSECONDS)
        stepContext.recordTimer("connection-time-success", Duration.ofNanos(durationNs))
    }

    override fun recordFailedConnectionTime(stepContext: StepContext<*, *>, durationNs: Long) {
        val meterId = MeterId("connection-time", stepContext.toMetersTags().and("status", "FAILURE"))
        timers.computeIfAbsent(meterId) { key -> meterRegistry.timer(key.name, key.tags) }
            .record(durationNs, TimeUnit.NANOSECONDS)
        stepContext.recordTimer("connection-time-failure", Duration.ofNanos(durationNs))
    }

    override fun recordSuccessfulTlsHandshakeTime(stepContext: StepContext<*, *>, durationNs: Long) {
        val meterId = MeterId("tls-handshake-time", stepContext.toMetersTags().and("status", "SUCCESS"))
        timers.computeIfAbsent(meterId) { key -> meterRegistry.timer(key.name, key.tags) }
            .record(durationNs, TimeUnit.NANOSECONDS)
        stepContext.recordTimer("tls-handshake-time-success", Duration.ofNanos(durationNs))
    }

    override fun recordFailedTlsHandshakeTime(stepContext: StepContext<*, *>, durationNs: Long) {
        val meterId = MeterId("tls-handshake-time", stepContext.toMetersTags().and("status", "FAILURE"))
        timers.computeIfAbsent(meterId) { key -> meterRegistry.timer(key.name, key.tags) }
            .record(durationNs, TimeUnit.NANOSECONDS)
        stepContext.recordTimer("tls-handshake-time-failure", Duration.ofNanos(durationNs))
    }

    override fun recordTimeToLastByte(stepContext: StepContext<*, *>, durationNs: Long) {
        val meterId = MeterId("time-to-last-byte", stepContext.toMetersTags())
        timers.computeIfAbsent(meterId) { key -> meterRegistry.timer(key.name, key.tags) }
            .record(durationNs, TimeUnit.NANOSECONDS)
        stepContext.recordTimer("time-to-last-byte", Duration.ofNanos(durationNs))
    }

    override fun recordDataSent(stepContext: StepContext<*, *>, count: Int) {
        val meterId = MeterId("data-sent", stepContext.toMetersTags())
        val convertedCount = count.toDouble()
        counters.computeIfAbsent(meterId) { key -> meterRegistry.counter(key.name, key.tags) }
            .increment(convertedCount)
        stepContext.recordCounter(meterId.name, convertedCount)
    }

    override fun recordDataReceived(stepContext: StepContext<*, *>, count: Int) {
        val meterId = MeterId("data-received", stepContext.toMetersTags())
        val convertedCount = count.toDouble()
        counters.computeIfAbsent(meterId) { key -> meterRegistry.counter(key.name, key.tags) }
            .increment(convertedCount)
        stepContext.recordCounter(meterId.name, convertedCount)
    }

    private data class MeterId(val name: String, val tags: Tags)
}
