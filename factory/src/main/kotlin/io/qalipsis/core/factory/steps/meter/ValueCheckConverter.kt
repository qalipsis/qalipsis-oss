package io.qalipsis.core.factory.steps.meter

import io.qalipsis.api.exceptions.QalipsisAssertionException
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.meterConditions.ComparableValueFailureSpecification
import io.qalipsis.api.meters.meterConditions.SpecificationType

class ValueCheckConverter {

    fun <T : Comparable<T>, M : Meter<M>> convert(
        spec: ComparableValueFailureSpecification<M, T>,
        meter: M
    ): QalipsisAssertionException? {
        val checkSpec = spec.checkSpec ?: return null
        val value = spec.valueExtractor(meter)
        val checker = when (checkSpec.type) {
            SpecificationType.LESS_THAN -> LessThanChecker(checkSpec.threshold)
            SpecificationType.MORE_THAN -> LessThanChecker(checkSpec.threshold)
            SpecificationType.IS_BETWEEN -> LessThanChecker(checkSpec.threshold)
        }

        return checker.check(value)
    }
}