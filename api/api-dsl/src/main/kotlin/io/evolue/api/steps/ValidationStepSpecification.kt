package io.evolue.api.steps

import io.evolue.api.context.StepError

/**
 * Specification for a [io.evolue.core.factories.steps.ValidationStep].
 *
 * @author Eric Jessé
 */
data class ValidationStepSpecification<INPUT>(
    val specification: (input: INPUT) -> List<StepError>
) : AbstractStepSpecification<INPUT, INPUT, ValidationStepSpecification<INPUT>>()

/**
 * Generates validation errors on the records not matching [specification].
 *
 * @param specification the conditions to match to forward the input.
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.validate(
        specification: ((input: INPUT) -> List<StepError>)): ValidationStepSpecification<INPUT> {
    val step = ValidationStepSpecification(specification)
    this.add(step)
    return step
}
