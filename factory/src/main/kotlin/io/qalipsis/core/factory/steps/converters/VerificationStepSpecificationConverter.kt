package io.qalipsis.core.factory.steps.converters

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.VerificationStepSpecification
import io.qalipsis.core.factory.steps.VerificationStep

/**
 * [StepSpecificationConverter] from [VerificationStepSpecification] to [VerificationStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class VerificationStepSpecificationConverter(
    private val eventsLogger: EventsLogger,
    private val meterRegistry: MeterRegistry,
    private val reportLiveStateRegistry: CampaignReportLiveStateRegistry
) : StepSpecificationConverter<VerificationStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is VerificationStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<VerificationStepSpecification<*, *>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as VerificationStepSpecification<I, O>
        // The reporting is done by the step itself and should not added by the decorator.
        spec.reporting.reportErrors = false
        val step =
            VerificationStep(spec.name, eventsLogger, meterRegistry, reportLiveStateRegistry, spec.verificationBlock)
        creationContext.createdStep(step)
    }

}
