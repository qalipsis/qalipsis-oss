package io.qalipsis.api.meters.meterConditions

import io.qalipsis.api.meters.Meter

class ComparableValueFailureSpecification<M : Meter<M>, T : Comparable<T>>(
    val valueExtractor: M.() -> T
) : FailureSpecification<T> {

    //To be converted into value checker
    var checkSpec: ValueCheckSpecification<T>? = null

    override fun isMoreThan(threshold: T) {
        checkSpec = MoreThanValueSpecification(threshold)
    }

    override fun isLessThan(threshold: T) {
        checkSpec = LessThanValueSpecification(threshold)
    }

    override fun isBetween(startThreshold: T, endThreshold: T) {
        checkSpec = LessThanValueSpecification(startThreshold)
    }

    override fun isNotBetween(startValue: T, endValue: T) {
        TODO("Not yet implemented")
    }

    override fun isEqual(threshold: T) {
        TODO("Not yet implemented")
    }

    override fun isGreaterThanOrEqual(threshold: T) {
        TODO("Not yet implemented")
    }

    override fun isLessThanOrEqual(threshold: T) {
        TODO("Not yet implemented")
    }

}