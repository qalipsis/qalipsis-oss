package io.qalipsis.api.meters

import io.qalipsis.api.meters.specification.FailureSpecification

interface CounterFailureConditionSpec {
    val count: FailureSpecification<Double>
}