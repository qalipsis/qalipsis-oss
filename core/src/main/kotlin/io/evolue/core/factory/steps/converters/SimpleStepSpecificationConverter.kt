package io.evolue.core.factory.steps.converters

import cool.graph.cuid.Cuid
import io.evolue.api.steps.SimpleStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.core.factory.steps.SimpleStep
import javax.inject.Singleton

/**
 * [StepSpecificationConverter] from [SimpleStepSpecification] to [SimpleStep].
 *
 * @author Eric Jessé
 */
@Singleton
internal class SimpleStepSpecificationConverter : StepSpecificationConverter<SimpleStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is SimpleStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<SimpleStepSpecification<*, *>>) {
        val spec = creationContext.stepSpecification as SimpleStepSpecification<I, O>
        val step = SimpleStep(spec.name ?: Cuid.createCuid(),
            spec.retryPolicy ?: creationContext.directedAcyclicGraph.scenario.defaultRetryPolicy,
            spec.specification)
        creationContext.createdStep(step)
    }

}
