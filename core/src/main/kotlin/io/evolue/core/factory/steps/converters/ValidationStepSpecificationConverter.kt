package io.evolue.core.factory.steps.converters

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.api.steps.ValidationStepSpecification
import io.evolue.core.factory.steps.ValidationStep

/**
 * [StepSpecificationConverter] from [ValidationStepSpecification] to [ValidationStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class ValidationStepSpecificationConverter :
    StepSpecificationConverter<ValidationStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is ValidationStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<ValidationStepSpecification<*>>) {
        val spec = creationContext.stepSpecification as ValidationStepSpecification<I>
        val step = ValidationStep(spec.name ?: Cuid.createCuid(),
            spec.retryPolicy ?: creationContext.directedAcyclicGraph.scenario.defaultRetryPolicy,
            spec.specification)
        creationContext.createdStep(step)
    }

}
