package io.qalipsis.core.factory.steps.meter

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.steps.GaugeMeterStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter

/**
 * [StepSpecificationConverter] from [CounterMeterStepSpecification] to [CounterMeterStep].
 *
 * @author Francisca Eze
 */
@StepConverter
internal class GaugeMeterStepSpecificationConverter(
    val meterRegistry: CampaignMeterRegistry,
) : StepSpecificationConverter<GaugeMeterStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is GaugeMeterStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<GaugeMeterStepSpecification<*>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as GaugeMeterStepSpecification<I>
        val step = GaugeMeterStep(
            spec.name,
            spec.retryPolicy ?: creationContext.directedAcyclicGraph.scenario.defaultRetryPolicy,
            spec.meterName,
            spec.block,
            spec.checks,
            meterRegistry
        )
        creationContext.createdStep(step)
    }

}
