package io.qalipsis.core.factory.steps.converters

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.PipeStepSpecification
import io.qalipsis.api.steps.SingletonPipeStepSpecification
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
internal class PipeStepSpecificationConverter : StepSpecificationConverter<StepSpecification<*, *, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is PipeStepSpecification || stepSpecification is SingletonPipeStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<StepSpecification<*, *, *>>) {
        val step = PipeStep<I>(creationContext.stepSpecification.name)
        creationContext.createdStep(step)
    }

}
