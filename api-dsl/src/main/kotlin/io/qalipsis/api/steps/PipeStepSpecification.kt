package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry

/**
 * Specification for a [io.qalipsis.core.factories.steps.PipeStep].
 *
 * @author Eric Jessé
 */
@Introspected
class PipeStepSpecification<INPUT> : AbstractStepSpecification<INPUT, INPUT, PipeStepSpecification<INPUT>>()

/**
 * Do nothing, just consumes the input and sends it to the output. This step does not bring any logic, but is used
 * to support special workflows (joins, splits...)
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.pipe(): PipeStepSpecification<INPUT> {
    val step = PipeStepSpecification<INPUT>()
    this.add(step)
    return step
}

/**
 * Do nothing, just consumes the input and sends it to the output. This step does not bring any logic, but is used
 * to support special workflows (joins, splits...)
 *
 * @author Eric Jessé
 */
fun <INPUT> ScenarioSpecification.pipe(): PipeStepSpecification<INPUT> {
    val step = PipeStepSpecification<INPUT>()
    (this as StepSpecificationRegistry).add(step)
    return step
}

/**
 * Specification for a [io.qalipsis.core.factories.steps.PipeStep], but acting as a singleton.
 *
 * @author Eric Jessé
 */
@Introspected
class SingletonPipeStepSpecification<INPUT> :
    SingletonStepSpecification,
    AbstractStepSpecification<INPUT, INPUT, PipeStepSpecification<INPUT>>() {

    override val singletonConfiguration = SingletonConfiguration(SingletonType.UNICAST)
}

/**
 * Do nothing, just consumes the input and sends it to the output. This step does not bring any logic, but is used
 * to support special workflows (joins, splits...)
 *
 * Contrary to [pipe], this function creates a tube as a singleton, meaning that it is visited to be executed only once.
 *
 * @see [pipe]
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.singletonPipe(): SingletonPipeStepSpecification<INPUT> {
    val step = SingletonPipeStepSpecification<INPUT>()
    this.add(step)
    return step
}
