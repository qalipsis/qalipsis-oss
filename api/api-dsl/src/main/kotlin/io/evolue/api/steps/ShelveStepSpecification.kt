package io.evolue.api.steps

/**
 * Specification for a [io.evolue.core.factory.steps.ShelveStep].
 *
 * @author Eric Jessé
 */
data class ShelveStepSpecification<INPUT>(
    val specification: (input: INPUT) -> Map<String, Any?>
) : AbstractStepSpecification<INPUT, INPUT, ShelveStepSpecification<INPUT>>()

fun <INPUT> StepSpecification<*, INPUT, *>.shelve(
    specification: (input: INPUT) -> Map<String, Any?>): ShelveStepSpecification<INPUT> {
    val step = ShelveStepSpecification(specification)
    this.add(step)
    return step
}

fun <INPUT> StepSpecification<*, INPUT, *>.shelve(name: String): ShelveStepSpecification<INPUT> {
    return this.shelve { input -> mapOf(name to input) }
}
