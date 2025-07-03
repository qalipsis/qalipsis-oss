package io.qalipsis.api.meters

import io.qalipsis.api.exceptions.QalipsisAssertionException

interface ValueChecker<T> {
    fun check(value: T): QalipsisAssertionException?
}
