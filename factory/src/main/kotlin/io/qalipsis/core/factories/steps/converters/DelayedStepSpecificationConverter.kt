package io.qalipsis.core.factories.steps.converters

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.DelayStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factories.steps.DelayStep

/**
 * [StepSpecificationConverter] from [DelayStepSpecification] to [DelayStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class DelayedStepSpecificationConverter : StepSpecificationConverter<DelayStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is DelayStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<DelayStepSpecification<*>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as DelayStepSpecification<I>
        val step = DelayStep<I>(spec.name, spec.duration)
        creationContext.createdStep(step)
    }


}
