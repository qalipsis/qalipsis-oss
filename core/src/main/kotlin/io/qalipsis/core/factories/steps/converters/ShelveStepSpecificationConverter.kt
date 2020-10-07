package io.qalipsis.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.api.steps.ShelveStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factories.steps.ShelveStep

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
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as ShelveStepSpecification<I>
        val step = ShelveStep(spec.name ?: Cuid.createCuid(), sharedStateRegistry, spec.specification)
        creationContext.createdStep(step)
    }

}
