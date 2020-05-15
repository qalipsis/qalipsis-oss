package io.evolue.api.steps

import io.evolue.api.context.StepError

/**
 * Specification for a [io.evolue.core.factory.steps.ValidationStep].
 *
 * @author Eric Jessé
 */
data class ValidationStepSpecification<INPUT>(
    val specification: (input: INPUT) -> List<StepError>
) : StepSpecification<INPUT, INPUT, ValidationStepSpecification<INPUT>>()

fun <INPUT> StepSpecification<*, INPUT, *>.validate(
    specification: ((input: INPUT) -> List<StepError>)): ValidationStepSpecification<INPUT> {
    val step = ValidationStepSpecification(specification)
    this.add(step)
    return step
}