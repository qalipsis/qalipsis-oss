package io.evolue.core.factory.steps.converters

import cool.graph.cuid.Cuid
import io.evolue.api.steps.FlatMapStepSpecification
import io.evolue.api.steps.MapStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.core.factory.steps.FlatMapStep
import io.evolue.core.factory.steps.MapStep
import javax.inject.Singleton

/**
 * [StepSpecificationConverter] from [FlatMapStepSpecification] to [FlatMapStep].
 *
 * @author Eric Jessé
 */
@Singleton
internal class MapStepSpecificationConverter :
    StepSpecificationConverter<MapStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is MapStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<MapStepSpecification<*, *>>) {
        val spec = creationContext.stepSpecification as MapStepSpecification<I, O>
        val step = MapStep(spec.name ?: Cuid.createCuid(),
            spec.retryPolicy ?: creationContext.directedAcyclicGraph.scenario.defaultRetryPolicy, spec.block)
        creationContext.createdStep(step)
    }

}
