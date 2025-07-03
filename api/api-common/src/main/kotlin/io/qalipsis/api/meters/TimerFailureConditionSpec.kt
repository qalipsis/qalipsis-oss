package io.qalipsis.api.meters

import io.qalipsis.api.meters.specification.FailureSpecification
import java.time.Duration

interface TimerFailureConditionSpec {
    fun percentile(percentile: Int): FailureSpecification<Duration>
    val max: FailureSpecification<Duration>
    val mean: FailureSpecification<Duration>
}