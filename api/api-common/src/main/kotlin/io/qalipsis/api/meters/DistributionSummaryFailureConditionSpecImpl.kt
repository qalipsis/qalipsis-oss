package io.qalipsis.api.meters

import io.qalipsis.api.meters.specification.ComparableValueFailureSpecification
import io.qalipsis.api.meters.specification.FailureSpecification

class DistributionSummaryFailureConditionSpecImpl : DistributionSummaryFailureConditionSpec {

    val checks = mutableListOf<ComparableValueFailureSpecification<DistributionSummary, Double>>()

    //@TODO verify this action here. Returning vcs vs returning unit.
    override val max: ComparableValueFailureSpecification<DistributionSummary, Double>
        get() {
            val vcs = ComparableValueFailureSpecification(DistributionSummary::max)
            checks.add(vcs)
            return vcs
        }

    override val mean: FailureSpecification<Double>
        get() {
            val vcs = ComparableValueFailureSpecification(DistributionSummary::mean)
            checks.add(vcs)
            return vcs
        }

    override fun percentile(percentile: Int): FailureSpecification<Double> {
        val valueExtractor: DistributionSummary.() -> Double = { percentile(percentile.toDouble()) }
        val vcs = ComparableValueFailureSpecification(valueExtractor)
        checks.add(vcs)
        return vcs
    }
}