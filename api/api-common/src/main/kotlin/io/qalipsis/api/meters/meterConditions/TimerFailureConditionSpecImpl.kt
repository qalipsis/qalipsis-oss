package io.qalipsis.api.meters.meterConditions

import io.qalipsis.api.meters.Timer
import java.time.Duration
import java.util.concurrent.TimeUnit

class TimerFailureConditionSpecImpl : TimerFailureConditionSpec {

    val checks = mutableListOf<FailureSpecification<Duration>>()

    //@TODO verify this action here. Returning vcs vs returning unit.
    override val max: ComparableValueFailureSpecification<Timer, Duration>
        get() {
            val valueExtractor: Timer.() -> Duration = {Duration.ofMillis(max(TimeUnit.MICROSECONDS).toLong())}
            val vcs = ComparableValueFailureSpecification(valueExtractor)
            checks.add(vcs)
            return vcs
        }

    override val average: FailureSpecification<Duration>
        get() {
            val valueExtractor: Timer.() -> Duration = {Duration.ofMillis(mean(TimeUnit.MICROSECONDS).toLong())}
            val vcs = ComparableValueFailureSpecification(valueExtractor)
            checks.add(vcs)
            return vcs
        }

    /**
     * @TODO fix the value extractor here.
     */
    override fun percentile(percentile: Int): FailureSpecification<Duration> {
        val valueExtractor: Timer.() -> Duration = {Duration.ofMillis(mean(TimeUnit.MICROSECONDS).toLong())}
        val vcs = ComparableValueFailureSpecification(valueExtractor)
        checks.add(vcs)
        return vcs
    }
}