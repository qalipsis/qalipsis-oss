package io.qalipsis.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.steps.VerificationStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factories.steps.VerificationStep

/**
 * [StepSpecificationConverter] from [VerificationStepSpecification] to [VerificationStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class VerificationStepSpecificationConverter(
    private val eventsLogger: EventsLogger,
    private val meterRegistry: MeterRegistry
) : StepSpecificationConverter<VerificationStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is VerificationStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<VerificationStepSpecification<*, *>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as VerificationStepSpecification<I, O>
        val step = VerificationStep(spec.name ?: Cuid.createCuid(), eventsLogger, meterRegistry, spec.verificationBlock)
        creationContext.createdStep(step)
    }

}
