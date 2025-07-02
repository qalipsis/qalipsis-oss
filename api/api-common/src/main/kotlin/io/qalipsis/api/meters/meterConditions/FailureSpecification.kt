package io.qalipsis.api.meters.meterConditions

interface FailureSpecification<T : Comparable<T>> {
    fun isGreaterThan(threshold: T)
    fun isLessThan(threshold: T)
    fun isBetween(threshold: T, lowerBound: T, upperBound: T)
    fun isNotBetween(threshold: T, lowerBound: T, upperBound: T)
    fun isEqual(threshold: T)
    fun isGreaterThanOrEqual(threshold: T)
    fun isLessThanOrEqual(threshold: T)
}
