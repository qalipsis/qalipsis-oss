package io.qalipsis.api.meters

import io.qalipsis.api.meters.specification.ComparableValueFailureSpecification

class GaugeFailureConditionSpecImpl : GaugeFailureConditionSpec {

    val checks = mutableListOf<ComparableValueFailureSpecification<Gauge, Double>>()

    //@TODO verify this action here. Returning vcs vs returning unit.
    override val count: ComparableValueFailureSpecification<Gauge, Double>
        get() {
            val vcs = ComparableValueFailureSpecification(Gauge::value)
            checks.add(vcs)
            return vcs
        }
}