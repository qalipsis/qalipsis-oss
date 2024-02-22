package io.qalipsis.core.factory.meters

import io.aerisconsulting.catadioptre.KTestable
import java.util.concurrent.atomic.DoubleAdder
import java.util.function.Supplier

// StepMillis here is equal to the step interval which is represented in milliseconds
/**
 * Utility class for managing a set of AtomicLong instances mapped to a particular step interval, similar to the [io.micrometer.core.instrument.step.StepDouble] library.
 *
 * @author Francisca Eze
 */
class StepDouble {

    @KTestable
    private var previous = noValue()

    val current = DoubleAdder()

    private fun noValue() = 0.0

    private fun valueSupplier(): Supplier<Double> = Supplier { current.sumThenReset() }

    fun count(): Double {
        val currentValue = valueSupplier().get()
        previous = currentValue
        return previous
    }
}
    