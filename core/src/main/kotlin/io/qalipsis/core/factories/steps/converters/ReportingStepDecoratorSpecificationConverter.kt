package io.qalipsis.core.factories.steps.converters

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.CampaignStateKeeper
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationDecoratorConverter
import io.qalipsis.core.factories.steps.ReportingStepDecorator

/**
 * [StepSpecificationDecoratorConverter] from any [AbstractStepSpecification] to [ReportingStepDecorator].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class ReportingStepDecoratorSpecificationConverter(
    private val campaignStateKeeper: CampaignStateKeeper
) : StepSpecificationDecoratorConverter<StepSpecification<*, *, *>>() {

    override val order: Int = 100

    override suspend fun decorate(creationContext: StepCreationContext<StepSpecification<*, *, *>>) {
        val spec = creationContext.stepSpecification
        if (spec.reporting.reportErrors) {
            creationContext.createdStep(ReportingStepDecorator(creationContext.createdStep!!, campaignStateKeeper))
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }

}
