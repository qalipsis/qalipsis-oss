package io.qalipsis.api.meters.meterConditions

import java.time.Duration

interface TimerFailureConditionSpec {
    fun percentile(percentile: Int): FailureSpecification<Duration>
    val max: FailureSpecification<Duration>
    val mean: FailureSpecification<Duration>
}