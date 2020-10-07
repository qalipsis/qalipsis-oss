package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected

/**
 * Specification for a [io.qalipsis.core.factories.steps.TubeStep].
 *
 * @author Eric Jessé
 */
@Introspected
class TubeStepSpecification<INPUT> : AbstractStepSpecification<INPUT, INPUT, TubeStepSpecification<INPUT>>()

/**
 * Do nothing, just consume the input and sends it to the output. This step does not bring any logic, but is used
 * to support special workflows (joins...)
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.tube(): TubeStepSpecification<INPUT> {
    val step = TubeStepSpecification<INPUT>()
    this.add(step)
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
 * Do nothing, just consume the input and sends it to the output. This step does not bring any logic, but is used
 * to support special workflows (joins...)
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.singletonTube(): SingletonTubeStepSpecification<INPUT> {
    val step = SingletonTubeStepSpecification<INPUT>()
    this.add(step)
    return step
}