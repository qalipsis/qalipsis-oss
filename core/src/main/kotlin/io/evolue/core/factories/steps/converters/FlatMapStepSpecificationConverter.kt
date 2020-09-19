package io.evolue.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.steps.FlatMapStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.core.factories.steps.FlatMapStep

/**
 * [StepSpecificationConverter] from [FlatMapStepSpecification] to [FlatMapStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class FlatMapStepSpecificationConverter :
    StepSpecificationConverter<FlatMapStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is FlatMapStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<FlatMapStepSpecification<*, *>>) {
        val spec = creationContext.stepSpecification as FlatMapStepSpecification<I, O>
        val step = FlatMapStep(spec.name ?: Cuid.createCuid(),
            spec.retryPolicy ?: creationContext.directedAcyclicGraph.scenario.defaultRetryPolicy, spec.block)
        creationContext.createdStep(step)
    }


}
