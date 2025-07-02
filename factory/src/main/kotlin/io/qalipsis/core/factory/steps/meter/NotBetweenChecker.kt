package io.qalipsis.core.factory.steps.meter

import io.qalipsis.api.exceptions.QalipsisAssertionException
import io.qalipsis.api.meters.meterConditions.ValueChecker

class NotBetweenChecker<T : Comparable<T>>(private val threshold: T,  private val lowerBound: T, private val upperBound: T) : ValueChecker<T> {

    override fun check(value: T): QalipsisAssertionException?{
        return if (isNotBetween(value, lowerBound, upperBound)) {
            QalipsisAssertionException("Duration $threshold should not be between bounds: $lowerBound and $upperBound")
        } else null
    }

    private fun isNotBetween(threshold: T,  lowerBound: T, upperBound: T): Boolean {
        return threshold !in lowerBound..upperBound
    }
}