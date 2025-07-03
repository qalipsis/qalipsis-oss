package io.qalipsis.api.meters

import io.qalipsis.api.meters.specification.FailureSpecification

interface RateFailureConditionSpec {
    val current: FailureSpecification<Double>
}