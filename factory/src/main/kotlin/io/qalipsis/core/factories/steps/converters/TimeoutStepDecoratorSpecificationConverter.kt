package io.qalipsis.core.factories.steps.converters

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationDecoratorConverter
import io.qalipsis.core.factories.steps.TimeoutStepDecorator

/**
 * [StepSpecificationDecoratorConverter] from any [AbstractStepSpecification] to [TimeoutStepDecorator].
 *
 * @author Eric Jessé
 */
@StepConverter
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
