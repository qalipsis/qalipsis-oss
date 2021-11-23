package io.qalipsis.core.factory.steps.converters

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.BlackHoleStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factory.steps.BlackHoleStep

/**
 * [StepSpecificationConverter] from [BlackHoleStepSpecification] to [BlackHoleStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class BlackHoleStepSpecificationConverter : StepSpecificationConverter<BlackHoleStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is BlackHoleStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<BlackHoleStepSpecification<*>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as BlackHoleStepSpecification<I>
        val step = BlackHoleStep<I, O>(spec.name)
        creationContext.createdStep(step)
    }

}
