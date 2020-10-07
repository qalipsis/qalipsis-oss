package io.qalipsis.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.PaceStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factories.steps.PaceStep

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
        val step = PaceStep<I>(spec.name ?: Cuid.createCuid(), spec.specification)
        creationContext.createdStep(step)
    }

}
