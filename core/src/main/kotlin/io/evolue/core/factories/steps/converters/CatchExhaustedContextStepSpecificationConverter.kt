package io.evolue.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.steps.CatchExhaustedContextStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.core.factories.steps.CatchExhaustedContextStep

/**
 * [StepSpecificationConverter] from [CatchExhaustedContextStepSpecification] to [CatchExhaustedContextStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class CatchExhaustedContextStepSpecificationConverter :
    StepSpecificationConverter<CatchExhaustedContextStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is CatchExhaustedContextStepSpecification
    }

    override suspend fun <I, O> convert(
        creationContext: StepCreationContext<CatchExhaustedContextStepSpecification<*, *>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as CatchExhaustedContextStepSpecification<I, O>
        val step = CatchExhaustedContextStep(spec.name ?: Cuid.createCuid(), spec.block)
        creationContext.createdStep(step)
    }

}
