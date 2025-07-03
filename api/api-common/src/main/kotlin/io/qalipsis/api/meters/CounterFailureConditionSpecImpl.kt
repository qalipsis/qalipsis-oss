package io.qalipsis.api.meters

import io.qalipsis.api.meters.specification.ComparableValueFailureSpecification

class CounterFailureConditionSpecImpl : CounterFailureConditionSpec {

    val checks = mutableListOf<ComparableValueFailureSpecification<Counter, Double>>()

    //@TODO verify this action here. Returning vcs vs returning unit.
    override val count: ComparableValueFailureSpecification<Counter, Double>
        get() {
            val vcs = ComparableValueFailureSpecification(Counter::count)
            checks.add(vcs)
            return vcs
        }
}