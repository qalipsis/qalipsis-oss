package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected

/**
 * Specification for a [io.qalipsis.core.factory.steps.MapStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class MapStepSpecification<INPUT, OUTPUT>(
    val block: (input: INPUT) -> OUTPUT
) : AbstractStepSpecification<INPUT, OUTPUT, MapStepSpecification<INPUT, OUTPUT>>()

/**
 * Converts any input into a different output.
 *
 * @param block the rule to convert the input into the output.
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.map(
    @Suppress(
        "UNCHECKED_CAST"
    ) block: (input: INPUT) -> OUTPUT = { value -> value as OUTPUT }
): MapStepSpecification<INPUT, OUTPUT> {
    val step = MapStepSpecification(block)
    this.add(step)
    return step
}
