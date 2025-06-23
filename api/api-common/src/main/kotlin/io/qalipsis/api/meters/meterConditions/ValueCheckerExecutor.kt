package io.qalipsis.api.meters.meterConditions

import io.qalipsis.api.meters.Meter

// Converter converts from spec to executable instance...
class ValueCheckerExecutor<M : Meter<M>, T : Comparable<T>>(
    private val valueExtractor: M.() -> T,
    private val checker: ValueChecker<T> // Executable concretion from the io.qalipsis.api.meters.meterConditions.ValueCheckSpecification
) {

    fun execute(meter: M): Boolean {
        val value = meter.valueExtractor()
        return checker.check(value)
    }
}

interface ValueChecker<T> {
    fun check(value: T): Boolean
}

class LessThanChecker<T : Comparable<T>>(private val threshold: T) : ValueChecker<T> {
    override fun check(value: T): Boolean = value < threshold
}