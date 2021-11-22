package io.qalipsis.core.factory.steps.converters

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.PaceStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factory.steps.PaceStep

/**
 * [StepSpecificationConverter] from [PaceStepSpecification] to [PaceStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class PaceStepSpecificationConverter :
    StepSpecificationConverter<PaceStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is PaceStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<PaceStepSpecification<*>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as PaceStepSpecification<I>
        val step = PaceStep<I>(spec.name, spec.specification)
        creationContext.createdStep(step)
    }

}
