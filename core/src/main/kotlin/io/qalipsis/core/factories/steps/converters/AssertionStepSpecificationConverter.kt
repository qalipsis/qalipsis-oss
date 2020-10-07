package io.qalipsis.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.steps.AssertionStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factories.steps.AssertionStep
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
