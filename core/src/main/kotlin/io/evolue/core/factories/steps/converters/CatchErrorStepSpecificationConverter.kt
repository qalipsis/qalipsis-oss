package io.evolue.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.steps.CatchErrorStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.core.factories.steps.CatchErrorStep

/**
 * [StepSpecificationConverter] from [CatchErrorStepSpecification] to [CatchErrorStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class CatchErrorStepSpecificationConverter : StepSpecificationConverter<CatchErrorStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is CatchErrorStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<CatchErrorStepSpecification<*>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as CatchErrorStepSpecification<I>
        val step = CatchErrorStep<I>(spec.name ?: Cuid.createCuid(), spec.block)
        creationContext.createdStep(step)
    }

}
