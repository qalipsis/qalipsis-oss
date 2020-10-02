package io.evolue.api.steps

import io.evolue.api.constraints.PositiveDuration
import io.evolue.api.exceptions.InvalidSpecificationException
import io.micronaut.core.annotation.Introspected
import java.time.Duration

/**
 * Specification for a [io.evolue.core.factories.steps.DelayedStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class DelayStepSpecification<INPUT>(
        @PositiveDuration val duration: Duration
) : AbstractStepSpecification<INPUT, INPUT, DelayStepSpecification<INPUT>>()

/**
 * Delays the workflow with the specified duration in milliseconds.
 *
 * @param duration the delay to set between the previous and next step, in millisecond.
 */
fun <INPUT> StepSpecification<*, INPUT, *>.delay(duration: Long): DelayStepSpecification<INPUT> {
    return delay(Duration.ofMillis(duration))
}

/**
 * Delays the workflow with the specified duration.
 *
 * @param duration the delay to set between the previous and next step.
 */
fun <INPUT> StepSpecification<*, INPUT, *>.delay(duration: Duration): DelayStepSpecification<INPUT> {
    if (duration.isZero || duration.isNegative) {
        throw InvalidSpecificationException("The delay should be at least 1 ms long")
    }
    val step = DelayStepSpecification<INPUT>(duration)
    this.add(step)
    return step
}
