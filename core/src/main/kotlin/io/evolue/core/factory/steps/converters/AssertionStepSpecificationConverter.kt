package io.evolue.core.factory.steps.converters

import cool.graph.cuid.Cuid
import io.evolue.api.events.EventLogger
import io.evolue.api.steps.AssertionStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.core.factory.steps.AssertionStep
import io.micrometer.core.instrument.MeterRegistry

/**
 * [StepSpecificationConverter] from [AssertionStepSpecification] to [AssertionStep].
 *
 * @author Eric Jessé
 */
internal class AssertionStepSpecificationConverter(
    private val eventLogger: EventLogger,
    private val meterRegistry: MeterRegistry
) : StepSpecificationConverter<AssertionStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is AssertionStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<AssertionStepSpecification<*, *>>) {
        val spec = creationContext.stepSpecification as AssertionStepSpecification<I, O>
        val step = AssertionStep(spec.name ?: Cuid.createCuid(), eventLogger, meterRegistry, spec.assertionBlock)
        creationContext.createdStep(step)
    }

}