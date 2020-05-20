package io.evolue.core.factory.steps.converters

import io.evolue.api.steps.AbstractStepSpecification
import io.evolue.api.steps.Step
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationDecoratorConverter
import io.evolue.core.factory.steps.TimeoutStepDecorator
import io.micrometer.core.instrument.MeterRegistry

/**
 * [StepSpecificationDecoratorConverter] from any [AbstractStepSpecification] to [TimeoutStepDecorator].
 *
 * @author Eric Jessé
 */
internal class TimeoutStepDecoratorSpecificationConverter(
    private val meterRegistry: MeterRegistry
) : StepSpecificationDecoratorConverter<StepSpecification<*, *, *>>() {

    override val order: Int = 500

    override suspend fun <I, O> decorate(creationContext: StepCreationContext<StepSpecification<*, *, *>>,
                                         decoratedStep: Step<I, O>): Step<*, *> {
        val spec = creationContext.stepSpecification
        return if (spec.timeout != null) {
            TimeoutStepDecorator(spec.timeout!!, decoratedStep, meterRegistry)
        } else {
            decoratedStep
        }
    }

}