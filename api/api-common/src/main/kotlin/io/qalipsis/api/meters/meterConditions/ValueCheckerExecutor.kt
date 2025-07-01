package io.qalipsis.api.meters.meterConditions

import io.qalipsis.api.exceptions.QalipsisAssertionException
import io.qalipsis.api.meters.Meter

// Converter converts from spec to executable instance...
class ValueCheckerExecutor<M : Meter<M>, T : Comparable<T>>(
    private val valueExtractor: M.() -> T,
    private val checker: ValueChecker<T> // Executable concretion from the io.qalipsis.api.meters.meterConditions.ValueCheckSpecification
) {

    fun execute(meter: M): QalipsisAssertionException? {
        val value = meter.valueExtractor()
        return checker.check(value)
    }
}
