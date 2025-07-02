package io.qalipsis.core.factory.steps.meter

import io.qalipsis.api.exceptions.QalipsisAssertionException
import io.qalipsis.api.meters.meterConditions.ValueChecker

class MoreThanChecker<T : Comparable<T>>(private val threshold: T) : ValueChecker<T> {

    override fun check(value: T): QalipsisAssertionException?{
        return if (threshold > value) {
            QalipsisAssertionException("Duration $threshold should not be less than $value")
        } else null
    }
}