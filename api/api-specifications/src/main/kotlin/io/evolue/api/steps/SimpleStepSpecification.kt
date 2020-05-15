package io.evolue.api.steps

import io.evolue.api.ScenarioSpecification
import io.evolue.api.context.StepContext

/**
 * Specification for a [io.evolue.core.factory.steps.SimpleStep].
 *
 * @author Eric Jessé
 */
data class SimpleStepSpecification<INPUT, OUTPUT>(
    val specification: suspend (context: StepContext<INPUT, OUTPUT>) -> Unit
) : StepSpecification<INPUT?, OUTPUT?, SimpleStepSpecification<INPUT, OUTPUT>>()

fun <INPUT, OUTPUT> ScenarioSpecification.execute(
    specification: suspend (context: StepContext<INPUT, OUTPUT>) -> Unit): SimpleStepSpecification<INPUT, OUTPUT> {
    val step = SimpleStepSpecification(specification)
    this.add(step)
    return step
}

fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.execute(
    specification: suspend (context: StepContext<INPUT, OUTPUT>) -> Unit): SimpleStepSpecification<INPUT, OUTPUT> {
    val step = SimpleStepSpecification(specification)
    this.add(step)
    return step
}