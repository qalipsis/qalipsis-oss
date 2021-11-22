package io.qalipsis.core.factory.steps.converters

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.PipeStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factory.steps.PipeStep

/**
 * [StepSpecificationConverter] from [PipeStepSpecification] to [PipeStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class SingletonPipeStepSpecificationConverter : StepSpecificationConverter<PipeStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is PipeStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<PipeStepSpecification<*>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as PipeStepSpecification<I>
        val step = PipeStep<I>(spec.name)
        creationContext.createdStep(step)
    }

}
