package io.evolue.core.factory.steps.converters

import io.evolue.api.steps.AbstractStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationDecoratorConverter
import io.evolue.core.factory.steps.TimeoutStepDecorator
import io.micrometer.core.instrument.MeterRegistry
import javax.inject.Singleton

/**
 * [StepSpecificationDecoratorConverter] from any [AbstractStepSpecification] to [TimeoutStepDecorator].
 *
 * @author Eric Jessé
 */
@Singleton
internal class TimeoutStepDecoratorSpecificationConverter(
    private val meterRegistry: MeterRegistry
) : StepSpecificationDecoratorConverter<StepSpecification<*, *, *>>() {

    override val order: Int = 500

    override suspend fun decorate(creationContext: StepCreationContext<StepSpecification<*, *, *>>) {
        val spec = creationContext.stepSpecification
        if (spec.timeout != null) {
            creationContext.createdStep(
                TimeoutStepDecorator(spec.timeout!!, creationContext.createdStep!!, meterRegistry))
        }
    }

}
