package io.evolue.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.steps.OnEachStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.core.factories.steps.OnEachStep

/**
 * [StepSpecificationConverter] from [OnEachStepSpecification] to [OnEachStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class OnEachStepSpecificationConverter : StepSpecificationConverter<OnEachStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is OnEachStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<OnEachStepSpecification<*>>) {
        val spec = creationContext.stepSpecification as OnEachStepSpecification<I>
        val step = OnEachStep(spec.name ?: Cuid.createCuid(),
                spec.retryPolicy ?: creationContext.directedAcyclicGraph.scenario.defaultRetryPolicy,
                spec.statement)
        creationContext.createdStep(step)
    }


}
