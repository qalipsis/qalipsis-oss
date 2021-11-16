package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.context.StepContext

/**
 * Specification for a [io.qalipsis.core.factories.steps.MapWithContextStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class MapWithContextStepSpecification<INPUT, OUTPUT>(
    val block: (context: StepContext<INPUT, OUTPUT>, input: INPUT) -> OUTPUT
) : AbstractStepSpecification<INPUT, OUTPUT, MapWithContextStepSpecification<INPUT, OUTPUT>>()

/**
 * Converts any input into a different output, also considering the context.
 *
 * @param block the rule to convert the input and the context into the output.
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.mapWithContext(
    @Suppress(
        "UNCHECKED_CAST"
    ) block: (context: StepContext<INPUT, OUTPUT>, input: INPUT) -> OUTPUT = { _, value -> value as OUTPUT }
): MapWithContextStepSpecification<INPUT, OUTPUT> {
    val step = MapWithContextStepSpecification(block)
    this.add(step)
    return step
}
