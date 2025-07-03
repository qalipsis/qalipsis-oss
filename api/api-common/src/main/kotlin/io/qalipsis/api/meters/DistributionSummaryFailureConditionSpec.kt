package io.qalipsis.api.meters

import io.qalipsis.api.meters.specification.FailureSpecification

interface DistributionSummaryFailureConditionSpec {
    fun percentile(percentile: Int): FailureSpecification<Double>
    val max: FailureSpecification<Double>
    val mean: FailureSpecification<Double>
}