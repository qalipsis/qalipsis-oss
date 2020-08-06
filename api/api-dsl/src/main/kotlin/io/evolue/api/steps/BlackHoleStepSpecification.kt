package io.evolue.api.steps

/**
 * Specification for a [io.evolue.core.factory.steps.BlackHoleStep].
 *
 * @author Eric Jessé
 */
class BlackHoleStepSpecification<INPUT> : AbstractStepSpecification<INPUT, Unit, BlackHoleStepSpecification<INPUT>>()

/**
 * Consumes the received data without producing anything.
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.blackHole(): BlackHoleStepSpecification<INPUT> {
    val step = BlackHoleStepSpecification<INPUT>()
    this.add(step)
    return step
}