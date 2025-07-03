package io.qalipsis.api.meters

import io.qalipsis.api.meters.specification.ComparableValueFailureSpecification
import io.qalipsis.api.meters.specification.FailureSpecification

class ThroughputFailureConditionSpecImpl : ThroughputFailureConditionSpec {

    val checks = mutableListOf<ComparableValueFailureSpecification<Throughput, Double>>()

    //@TODO verify this action here. Returning vcs vs returning unit.
    override val max: ComparableValueFailureSpecification<Throughput, Double>
        get() {
            val vcs = ComparableValueFailureSpecification(Throughput::max)
            checks.add(vcs)
            return vcs
        }

    override val mean: FailureSpecification<Double>
        get() {
            val vcs = ComparableValueFailureSpecification(Throughput::mean)
            checks.add(vcs)
            return vcs
        }

    override val current: FailureSpecification<Double>
        get() {
            val vcs = ComparableValueFailureSpecification(Throughput::current)
            checks.add(vcs)
            return vcs
        }

    override fun percentile(percentile: Int): FailureSpecification<Double> {
        val valueExtractor: Throughput.() -> Double = { percentile(percentile.toDouble()) }
        val vcs = ComparableValueFailureSpecification(valueExtractor)
        checks.add(vcs)
        return vcs
    }
}