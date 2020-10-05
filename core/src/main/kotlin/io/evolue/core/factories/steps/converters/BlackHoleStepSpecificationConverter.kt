package io.evolue.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.steps.BlackHoleStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.core.factories.steps.BlackHoleStep

/**
 * [StepSpecificationConverter] from [BlackHoleStepSpecification] to [BlackHoleStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class BlackHoleStepSpecificationConverter : StepSpecificationConverter<BlackHoleStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is BlackHoleStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<BlackHoleStepSpecification<*>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as BlackHoleStepSpecification<I>
        val step = BlackHoleStep<I, O>(spec.name ?: Cuid.createCuid())
        creationContext.createdStep(step)
    }

}
