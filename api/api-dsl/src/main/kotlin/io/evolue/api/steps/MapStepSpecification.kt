package io.evolue.api.steps

/**
 * Specification for a [io.evolue.core.factory.steps.MapStep].
 *
 * @author Eric Jessé
 */
data class MapStepSpecification<INPUT, OUTPUT>(
    val block: (input: INPUT) -> OUTPUT
) : AbstractStepSpecification<INPUT, OUTPUT, MapStepSpecification<INPUT, OUTPUT>>()

fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.map(
    block: (input: INPUT) -> OUTPUT = { value -> value as OUTPUT }): MapStepSpecification<INPUT, OUTPUT> {
    val step = MapStepSpecification(block)
    this.add(step)
    return step
}