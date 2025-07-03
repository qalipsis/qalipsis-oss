package io.qalipsis.core.factory.steps.meter.checkers

import io.qalipsis.api.exceptions.QalipsisAssertionException
import io.qalipsis.api.meters.ValueChecker

class EqualChecker<T : Comparable<T>>(private val threshold: T) : ValueChecker<T> {

    override fun check(value: T): QalipsisAssertionException?{
        return if (threshold == value) {
            QalipsisAssertionException("Duration $threshold is not equal to $value.")
        } else null
    }
}