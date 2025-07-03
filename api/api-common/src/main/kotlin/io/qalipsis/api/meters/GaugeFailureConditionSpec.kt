package io.qalipsis.api.meters

import io.qalipsis.api.meters.specification.FailureSpecification

interface GaugeFailureConditionSpec {
    val count: FailureSpecification<Double>
}