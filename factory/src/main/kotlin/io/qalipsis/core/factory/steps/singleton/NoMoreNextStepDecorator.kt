package io.qalipsis.core.factory.steps.singleton

import io.qalipsis.api.context.StepName
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepDecorator

/**
 * Decorator for a step on which no more step step can be added.
 *
 * @author Eric Jessé
 */
class NoMoreNextStepDecorator<I, O>(
    override val decorated: Step<I, O>
) : Step<I, O>, StepDecorator<I, O> {

    override val name: StepName = decorated.name

    override var retryPolicy = decorated.retryPolicy

    override val next: List<Step<O, *>>
        get() = decorated.next

    override fun addNext(nextStep: Step<*, *>) {
        // Do nothing.
    }

}
