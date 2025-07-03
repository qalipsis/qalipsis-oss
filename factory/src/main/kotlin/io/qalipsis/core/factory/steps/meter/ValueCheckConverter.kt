package io.qalipsis.core.factory.steps.meter

import io.qalipsis.api.exceptions.QalipsisAssertionException
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.specification.BetweenValueSpecification
import io.qalipsis.api.meters.specification.ComparableValueFailureSpecification
import io.qalipsis.api.meters.specification.NotBetweenValueSpecification
import io.qalipsis.api.meters.specification.SpecificationType
import io.qalipsis.core.factory.steps.meter.checkers.BetweenChecker
import io.qalipsis.core.factory.steps.meter.checkers.EqualChecker
import io.qalipsis.core.factory.steps.meter.checkers.GreaterThanOrEqualChecker
import io.qalipsis.core.factory.steps.meter.checkers.LessThanChecker
import io.qalipsis.core.factory.steps.meter.checkers.LessThanOrEqualChecker
import io.qalipsis.core.factory.steps.meter.checkers.MoreThanChecker
import io.qalipsis.core.factory.steps.meter.checkers.NotBetweenChecker

class ValueCheckConverter {

    fun <T : Comparable<T>, M : Meter<M>> convert(
        spec: ComparableValueFailureSpecification<M, T>,
        meter: M
    ): QalipsisAssertionException? {
        val checkSpec = spec.checkSpec ?: return null
        val value = spec.valueExtractor(meter)
        val checker = when (checkSpec.type) {
            SpecificationType.LESS_THAN -> LessThanChecker(checkSpec.threshold)
            SpecificationType.MORE_THAN -> MoreThanChecker(checkSpec.threshold)
            SpecificationType.BETWEEN -> {
                checkSpec as BetweenValueSpecification
                BetweenChecker(checkSpec.threshold, checkSpec.lowerBound, checkSpec.upperBound)
            }

            SpecificationType.NOT_BETWEEN -> {
                checkSpec as NotBetweenValueSpecification
                NotBetweenChecker(checkSpec.threshold, checkSpec.lowerBound, checkSpec.upperBound)
            }
            SpecificationType.EQUAL -> EqualChecker(checkSpec.threshold)
            SpecificationType.GREATER_THAN_OR_EQUAL -> GreaterThanOrEqualChecker(checkSpec.threshold)
            SpecificationType.LESS_THAN_OR_EQUAL -> LessThanOrEqualChecker(checkSpec.threshold)
        }

        return checker.check(value)
    }
}