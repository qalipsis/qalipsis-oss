package io.qalipsis.core.factories.steps.converters

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.CatchExhaustedContextStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factories.steps.CatchExhaustedContextStep

/**
 * [StepSpecificationConverter] from [CatchExhaustedContextStepSpecification] to [CatchExhaustedContextStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class CatchExhaustedContextStepSpecificationConverter :
    StepSpecificationConverter<CatchExhaustedContextStepSpecification< *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is CatchExhaustedContextStepSpecification
    }

    override suspend fun <I, O> convert(
        creationContext: StepCreationContext<CatchExhaustedContextStepSpecification<*>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as CatchExhaustedContextStepSpecification<O>
        val step = CatchExhaustedContextStep(spec.name, spec.block)
        creationContext.createdStep(step)
    }

}
