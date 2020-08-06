package io.evolue.api.steps

import io.evolue.api.context.StepContext
import io.evolue.api.scenario.MutableScenarioSpecification
import io.evolue.api.scenario.ScenarioSpecification

/**
 * Specification for a [io.evolue.core.factory.steps.SimpleStep].
 *
 * @author Eric Jessé
 */
data class SimpleStepSpecification<INPUT, OUTPUT>(
        val specification: suspend (context: StepContext<INPUT, OUTPUT>) -> Unit
) : AbstractStepSpecification<INPUT?, OUTPUT?, SimpleStepSpecification<INPUT, OUTPUT>>()

/**
 * Executes [specification] on the provided context.
 *
 * @param specification statements to perform on the context.
 *
 * @author Eric Jessé
 */
fun <OUTPUT> ScenarioSpecification.execute(
        specification: suspend (context: StepContext<Unit, OUTPUT>) -> Unit): SimpleStepSpecification<Unit, OUTPUT> {
    val step = SimpleStepSpecification(specification)
    (this as MutableScenarioSpecification).add(step)
    return step
}

/**
 * Forwards a constant value to next steps.
 *
 * @param value the constant value to forward.
 *
 * @author Eric Jessé
 */
fun <OUTPUT> ScenarioSpecification.returns(value: OUTPUT): SimpleStepSpecification<Unit, OUTPUT> {
    val step = SimpleStepSpecification<Unit, OUTPUT> {
        it.output.send(value)
    }
    (this as MutableScenarioSpecification).add(step)
    return step
}

/**
 * Forwards a value to next steps.
 *
 * @param specification the statements to generate the value to forward.
 *
 * @author Eric Jessé
 */
fun <OUTPUT> ScenarioSpecification.returns(
        specification: suspend (context: StepContext<Unit, OUTPUT>) -> OUTPUT): SimpleStepSpecification<Unit, OUTPUT> {
    val step = SimpleStepSpecification<Unit, OUTPUT> {
        it.output.send(specification(it))
    }
    (this as MutableScenarioSpecification).add(step)
    return step
}

/**
 * Executes [specification] on the provided context.
 *
 * @param specification statements to perform on the context.
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.execute(
        specification: suspend (context: StepContext<INPUT, OUTPUT>) -> Unit): SimpleStepSpecification<INPUT, OUTPUT> {
    val step = SimpleStepSpecification(specification)
    this.add(step)
    return step
}
