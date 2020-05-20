package io.evolue.core.factory.steps.converters

import cool.graph.cuid.Cuid
import io.evolue.api.steps.CatchErrorStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.core.factory.steps.CatchErrorStep

/**
 * [StepSpecificationConverter] from [CatchErrorStepSpecification] to [CatchErrorStep].
 *
 * @author Eric Jessé
 */
internal class CatchErrorStepSpecificationConverter : StepSpecificationConverter<CatchErrorStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is CatchErrorStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<CatchErrorStepSpecification<*>>) {
        val spec = creationContext.stepSpecification as CatchErrorStepSpecification<I>
        val step = CatchErrorStep<I>(spec.name ?: Cuid.createCuid(), spec.block)
        creationContext.createdStep(step)
    }

}