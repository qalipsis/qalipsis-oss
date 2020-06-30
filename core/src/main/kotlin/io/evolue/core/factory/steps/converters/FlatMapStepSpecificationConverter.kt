package io.evolue.core.factory.steps.converters

import cool.graph.cuid.Cuid
import io.evolue.api.steps.FlatMapStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.core.factory.steps.FlatMapStep
import javax.inject.Singleton

/**
 * [StepSpecificationConverter] from [FlatMapStepSpecification] to [FlatMapStep].
 *
 * @author Eric Jessé
 */
@Singleton
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
