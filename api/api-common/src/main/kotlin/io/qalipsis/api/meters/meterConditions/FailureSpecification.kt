package io.qalipsis.api.meters.meterConditions

interface FailureSpecification<T : Comparable<T>> {
    //@TODO add more functions as far as they are sensible
    fun isMoreThan(threshold: T)
    fun isLessThan(threshold: T)
    fun isBetween(startThreshold: T, endThreshold: T)
//    fun isNotBetween(startValue: T, endValue: T)
//    fun isEqual(threshold: T)
}

//interface Exec {
//    fun <T : Meter<T>>execute(meter: Meter<T>)
//}
//
//
//interface CheckSpecification<T> {
//    fun validate(input: T): QalipsisAssertionException?
//}
