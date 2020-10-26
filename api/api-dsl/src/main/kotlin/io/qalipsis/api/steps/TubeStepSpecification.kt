package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry

/**
 * Specification for a [io.qalipsis.core.factories.steps.TubeStep].
 *
 * @author Eric Jessé
 */
@Introspected
class TubeStepSpecification<INPUT> : AbstractStepSpecification<INPUT, INPUT, TubeStepSpecification<INPUT>>()

/**
 * Do nothing, just consumes the input and sends it to the output. This step does not bring any logic, but is used
 * to support special workflows (joins, splits...)
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.tube(): TubeStepSpecification<INPUT> {
    val step = TubeStepSpecification<INPUT>()
    this.add(step)
    return step
}

/**
 * Do nothing, just consumes the input and sends it to the output. This step does not bring any logic, but is used
 * to support special workflows (joins, splits...)
 *
 * @author Eric Jessé
 */
fun <INPUT> ScenarioSpecification.tube(): TubeStepSpecification<INPUT> {
    val step = TubeStepSpecification<INPUT>()
    (this as StepSpecificationRegistry).add(step)
    return step
}

/**
 * Specification for a [io.qalipsis.core.factories.steps.TubeStep], but acting as a singleton.
 *
 * @author Eric Jessé
 */
@Introspected
class SingletonTubeStepSpecification<INPUT> :
    SingletonStepSpecification<INPUT, INPUT, SingletonTubeStepSpecification<INPUT>>,
    AbstractStepSpecification<INPUT, INPUT, SingletonTubeStepSpecification<INPUT>>() {

    override val singletonConfiguration = SingletonConfiguration(SingletonType.UNICAST)
}

/**
 * Do nothing, just consumes the input and sends it to the output. This step does not bring any logic, but is used
 * to support special workflows (joins, splits...)
 *
 * Contrary to [tube], this function creates a tube as a singleton, meaning that it is visited to be executed only once.
 *
 * @see [tube]
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.singletonTube(): SingletonTubeStepSpecification<INPUT> {
    val step = SingletonTubeStepSpecification<INPUT>()
    this.add(step)
    return step
}
