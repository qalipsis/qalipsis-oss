package io.evolue.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.states.SharedStateRegistry
import io.evolue.api.steps.ShelveStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.core.factories.steps.ShelveStep

/**
 * [StepSpecificationConverter] from [ShelveStepSpecification] to [ShelveStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class ShelveStepSpecificationConverter(
    private val sharedStateRegistry: SharedStateRegistry
) : StepSpecificationConverter<ShelveStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is ShelveStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<ShelveStepSpecification<*>>) {
        val spec = creationContext.stepSpecification as ShelveStepSpecification<I>
        val step = ShelveStep(spec.name ?: Cuid.createCuid(), sharedStateRegistry, spec.specification)
        creationContext.createdStep(step)
    }

}
