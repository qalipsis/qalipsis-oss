package io.evolue.api.steps

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Specification for a [io.evolue.core.factories.steps.FlatMapStep].
 *
 * @author Eric Jessé
 */
data class FlatMapStepSpecification<INPUT, OUTPUT>(
        val block: (input: INPUT) -> Flow<OUTPUT>
) : AbstractStepSpecification<INPUT, OUTPUT, FlatMapStepSpecification<INPUT, OUTPUT>>()

/**
 * Converts any input into a [Flow] of records provided one by one
 * to the next step.
 *
 * @param block the rule to convert the input into a [Flow].
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.flatMap(
        block: (input: INPUT) -> Flow<OUTPUT>): FlatMapStepSpecification<INPUT, OUTPUT> {
    val step = FlatMapStepSpecification(block)
    this.add(step)
    return step
}

/**
 * Converts any input of type [Collection], [Array], [Sequence], [Map] into a [Flow] of records provided one by one
 * to the next step.
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.flatten(): FlatMapStepSpecification<INPUT, OUTPUT> {
    return flatMap { input ->
        when (input) {
            null -> emptyFlow()
            is Collection<*> ->
                input.asFlow() as Flow<OUTPUT>

            is Array<*> ->
                input.asFlow() as Flow<OUTPUT>

            is Sequence<*> ->
                input.asFlow() as Flow<OUTPUT>

            is Map<*, *> ->
                input.entries.map { e -> e.key to e.value }.asFlow() as Flow<OUTPUT>

            else ->
                flowOf(input) as Flow<OUTPUT>
        }
    }
}
