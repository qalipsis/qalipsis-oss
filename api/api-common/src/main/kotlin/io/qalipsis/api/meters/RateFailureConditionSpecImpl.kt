package io.qalipsis.api.meters

import io.qalipsis.api.meters.specification.ComparableValueFailureSpecification
import io.qalipsis.api.meters.specification.FailureSpecification

class RateFailureConditionSpecImpl : RateFailureConditionSpec {

    val checks = mutableListOf<ComparableValueFailureSpecification<Rate, Double>>()

    //@TODO verify this action here. Returning vcs vs returning unit.

    override val current: FailureSpecification<Double>
        get() {
            val vcs = ComparableValueFailureSpecification(Rate::current)
            checks.add(vcs)
            return vcs
        }
}