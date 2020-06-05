package io.evolue.api.steps

import io.evolue.api.context.StepContext
import io.evolue.api.scenario.ScenarioSpecification

/**
 * Specification for a [io.evolue.core.factory.steps.SimpleStep].
 *
 * @author Eric Jessé
 */
data class SimpleStepSpecification<INPUT, OUTPUT>(
    val specification: suspend (context: StepContext<INPUT, OUTPUT>) -> Unit
) : AbstractStepSpecification<INPUT?, OUTPUT?, SimpleStepSpecification<INPUT, OUTPUT>>()

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