package io.qalipsis.core.factories.steps.converters

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.CatchErrorsStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factories.steps.CatchErrorsStep

/**
 * [StepSpecificationConverter] from [CatchErrorsStepSpecification] to [CatchErrorsStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class CatchErrorsStepSpecificationConverter : StepSpecificationConverter<CatchErrorsStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is CatchErrorsStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<CatchErrorsStepSpecification<*>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification
        val step = CatchErrorsStep<I>(spec.name, spec.block)
        creationContext.createdStep(step)
    }

}
