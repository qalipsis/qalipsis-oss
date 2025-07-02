package io.qalipsis.api.meters.meterConditions

import io.qalipsis.api.meters.Meter

class ComparableValueFailureSpecification<M : Meter<M>, T : Comparable<T>>(
    val valueExtractor: M.() -> T
) : FailureSpecification<T> {

    var checkSpec: ValueCheckSpecification<T>? = null

    override fun isGreaterThan(threshold: T) {
        checkSpec = GreaterThanValueSpecification(threshold)
    }

    override fun isLessThan(threshold: T) {
        checkSpec = LessThanValueSpecification(threshold)
    }

    override fun isBetween(threshold: T, lowerBound: T, upperBound: T) {
        checkSpec = BetweenValueSpecification(threshold, lowerBound, upperBound)
    }

    override fun isNotBetween(threshold: T, lowerBound: T, upperBound: T) {
        checkSpec = NotBetweenValueSpecification(threshold, lowerBound, upperBound)
    }

    override fun isEqual(threshold: T) {
        checkSpec = EqualValueSpecification(threshold)
    }

    override fun isGreaterThanOrEqual(threshold: T) {
        checkSpec = GreaterThanOrEqualValueSpecification(threshold)
    }

    override fun isLessThanOrEqual(threshold: T) {
        checkSpec = GreaterThanOrEqualValueSpecification(threshold)
    }

}