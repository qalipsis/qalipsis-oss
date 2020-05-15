package io.evolue.api.steps

import io.evolue.api.exceptions.InvalidSpecificationException
import java.time.Duration

/**
 * Specification for a [io.evolue.core.factory.steps.FilterStep].
 *
 * @author Eric Jessé
 */
data class DelayDecoratorSpecification<INPUT>(
    val duration: Duration
) : StepSpecification<INPUT, INPUT, DelayDecoratorSpecification<INPUT>>() {

    internal var decoratedStep: StepSpecification<INPUT, *, *>? = null

    override fun add(step: StepSpecification<*, *, *>) {
        if (decoratedStep != null) {
            throw InvalidSpecificationException("Only one step can be added to a delay specification.")
        }
        scenario!!.register(step)
        decoratedStep = step as StepSpecification<INPUT, *, *>

        this.name = step.name
        scenario!!.register(this)
    }
}

fun <INPUT> StepSpecification<*, INPUT, *>.delay(duration: Long): DelayDecoratorSpecification<INPUT> {
    return delay(Duration.ofMillis(duration))
}

fun <INPUT> StepSpecification<*, INPUT, *>.delay(duration: Duration): DelayDecoratorSpecification<INPUT> {
    if (duration.isZero || duration.isNegative) {
        throw InvalidSpecificationException("The delay should be at least 1 ms long")
    }
    val step = DelayDecoratorSpecification<INPUT>(duration)
    this.add(step)
    return step
}