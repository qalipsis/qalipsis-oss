package io.evolue.core.factory.steps.converters

import cool.graph.cuid.Cuid
import io.evolue.api.steps.CatchExhaustedContextStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.core.factory.steps.CatchExhaustedContextStep
import javax.inject.Singleton

/**
 * [StepSpecificationConverter] from [CatchExhaustedContextStepSpecification] to [CatchExhaustedContextStep].
 *
 * @author Eric Jessé
 */
@Singleton
internal class CatchExhaustedContextStepSpecificationConverter :
    StepSpecificationConverter<CatchExhaustedContextStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is CatchExhaustedContextStepSpecification
    }

    override suspend fun <I, O> convert(
        creationContext: StepCreationContext<CatchExhaustedContextStepSpecification<*, *>>) {
        val spec = creationContext.stepSpecification as CatchExhaustedContextStepSpecification<I, O>
        val step = CatchExhaustedContextStep(spec.name ?: Cuid.createCuid(), spec.block)
        creationContext.createdStep(step)
    }

}
