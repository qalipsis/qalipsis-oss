package io.evolue.core.factory.steps.converters

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.steps.PaceStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.core.factory.steps.PaceStep

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
        val spec = creationContext.stepSpecification as PaceStepSpecification<I>
        val step = PaceStep<I>(spec.name ?: Cuid.createCuid(), spec.specification)
        creationContext.createdStep(step)
    }

}
