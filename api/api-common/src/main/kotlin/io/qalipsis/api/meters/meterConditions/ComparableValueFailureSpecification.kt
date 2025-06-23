package io.qalipsis.api.meters.meterConditions

import io.qalipsis.api.meters.Meter

class ComparableValueFailureSpecification<M : Meter<M>, T : Comparable<T>>(
    val valueExtractor: M.() -> T
) : FailureSpecification<T> {

    //To be converted into value checker
    private var checkSpec: ValueCheckSpecification<T>? = null

    override fun isMoreThan(threshold: T) {
        checkSpec = LessThanValueSpecification(threshold)
    }

    override fun isLessThan(threshold: T) {
        checkSpec = LessThanValueSpecification(threshold)
    }

    override fun isBetween(startThreshold: T, endThreshold: T) {
        checkSpec = LessThanValueSpecification(startThreshold)
    }

}