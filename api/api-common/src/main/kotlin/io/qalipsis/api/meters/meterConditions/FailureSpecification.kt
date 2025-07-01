package io.qalipsis.api.meters.meterConditions

interface FailureSpecification<T : Comparable<T>> {
    fun isMoreThan(threshold: T)
    fun isLessThan(threshold: T)
    fun isBetween(startThreshold: T, endThreshold: T)
    fun isNotBetween(startValue: T, endValue: T)
    fun isEqual(threshold: T)
    fun isGreaterThanOrEqual(threshold: T)
    fun isLessThanOrEqual(threshold: T)
}
