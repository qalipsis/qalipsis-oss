package io.qalipsis.core.factory.steps.meter.checkers

import io.qalipsis.api.exceptions.QalipsisAssertionException
import io.qalipsis.api.meters.ValueChecker

class LessThanChecker<T : Comparable<T>>(private val threshold: T) : ValueChecker<T> {

    override fun check(value: T): QalipsisAssertionException?{
        return if (value > threshold) {
            QalipsisAssertionException("Duration $value should not be more than $threshold")
        } else null
    }
}