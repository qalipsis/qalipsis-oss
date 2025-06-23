package io.qalipsis.api.meters.meterConditions

import java.time.Duration

interface TimerFailureConditionSpec {
//    fun percentile(percentile: Int): DurationFailureCondition
//    val max: DurationFailureCondition
//    val average: DurationFailureCondition
//    val mean: DurationFailureCondition

    fun percentile(percentile: Int): FailureSpecification<Duration>
    val max: FailureSpecification<Duration>
    val average: FailureSpecification<Duration>
}