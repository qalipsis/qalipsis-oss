package io.qalipsis.core.factory.steps.meter.checkers

import io.qalipsis.api.exceptions.QalipsisAssertionException
import io.qalipsis.api.meters.ValueChecker

class BetweenChecker<T : Comparable<T>>(private val threshold: T, private val lowerBound: T, private val upperBound: T) :
    ValueChecker<T> {

    override fun check(value: T): QalipsisAssertionException?{
        return if (isBetween(value, lowerBound, upperBound)) {
            QalipsisAssertionException("Duration $threshold should be between bounds: $lowerBound and $upperBound")
        } else null
    }

    private fun isBetween(threshold: T,  lowerBound: T, upperBound: T): Boolean {
        return threshold in lowerBound..upperBound
    }
}