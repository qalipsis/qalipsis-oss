package io.evolue.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.states.SharedStateRegistry
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.api.steps.UnshelveStepSpecification
import io.evolue.core.factories.steps.SingularUnshelveStep
import io.evolue.core.factories.steps.UnshelveStep

/**
 * [StepSpecificationConverter] from [UnshelveStepSpecification] to [UnshelveStep] and [SingularUnshelveStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class UnshelveStepSpecificationConverter(
    private val sharedStateRegistry: SharedStateRegistry
) : StepSpecificationConverter<UnshelveStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is UnshelveStepSpecification<*, *>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<UnshelveStepSpecification<*, *>>) {
        val spec = creationContext.stepSpecification as UnshelveStepSpecification<I, O>
        val step = if (spec.singular) {
            SingularUnshelveStep<I, O>(spec.name ?: Cuid.createCuid(), sharedStateRegistry, spec.names.first(),
                spec.delete)
        } else {
            UnshelveStep<I>(spec.name ?: Cuid.createCuid(), sharedStateRegistry, spec.names, spec.delete)
        }
        creationContext.createdStep(step)
    }

}
