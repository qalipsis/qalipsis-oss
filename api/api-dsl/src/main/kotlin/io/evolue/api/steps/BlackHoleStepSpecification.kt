package io.evolue.api.steps

import io.micronaut.core.annotation.Introspected

/**
 * Specification for a [io.evolue.core.factories.steps.BlackHoleStep].
 *
 * @author Eric Jessé
 */
@Introspected
class BlackHoleStepSpecification<INPUT> : AbstractStepSpecification<INPUT, Unit, BlackHoleStepSpecification<INPUT>>()

/**
 * Consumes the received data without producing anything.
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.blackHole(): BlackHoleStepSpecification<INPUT> {
    val step = BlackHoleStepSpecification<INPUT>()
    this.add(step)
    return step
}
