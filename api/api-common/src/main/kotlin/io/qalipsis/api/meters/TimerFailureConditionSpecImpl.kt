package io.qalipsis.api.meters

import io.qalipsis.api.meters.specification.ComparableValueFailureSpecification
import io.qalipsis.api.meters.specification.FailureSpecification
import java.time.Duration
import java.util.concurrent.TimeUnit

class TimerFailureConditionSpecImpl : TimerFailureConditionSpec {

    val checks = mutableListOf<ComparableValueFailureSpecification<Timer, Duration>>()

    //@TODO verify this action here. Returning vcs vs returning unit.
    override val max: FailureSpecification<Duration>
        get() {
            val valueExtractor: Timer.() -> Duration = { Duration.ofMillis(max(TimeUnit.MICROSECONDS).toLong()) }
            val vcs = ComparableValueFailureSpecification(valueExtractor)
            checks.add(vcs)
            return vcs
        }

    override val mean: FailureSpecification<Duration>
        get() {
            val valueExtractor: Timer.() -> Duration = { Duration.ofMillis(mean(TimeUnit.MICROSECONDS).toLong()) }
            val vcs = ComparableValueFailureSpecification(valueExtractor)
            checks.add(vcs)
            return vcs
        }

    override fun percentile(percentile: Int): FailureSpecification<Duration> {
        val valueExtractor: Timer.() -> Duration = { Duration.ofMillis(percentile(percentile.toDouble(), TimeUnit.MICROSECONDS).toLong()) }
        val vcs = ComparableValueFailureSpecification(valueExtractor)
        checks.add(vcs)
        return vcs
    }
}