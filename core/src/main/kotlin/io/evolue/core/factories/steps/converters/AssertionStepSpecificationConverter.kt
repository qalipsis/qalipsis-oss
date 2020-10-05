package io.evolue.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.events.EventsLogger
import io.evolue.api.steps.AssertionStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.core.factories.steps.AssertionStep
import io.micrometer.core.instrument.MeterRegistry

/**
 * [StepSpecificationConverter] from [AssertionStepSpecification] to [AssertionStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class AssertionStepSpecificationConverter(
    private val eventsLogger: EventsLogger,
    private val meterRegistry: MeterRegistry
) : StepSpecificationConverter<AssertionStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is AssertionStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<AssertionStepSpecification<*, *>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as AssertionStepSpecification<I, O>
        val step = AssertionStep(spec.name ?: Cuid.createCuid(), eventsLogger, meterRegistry, spec.assertionBlock)
        creationContext.createdStep(step)
    }

}
