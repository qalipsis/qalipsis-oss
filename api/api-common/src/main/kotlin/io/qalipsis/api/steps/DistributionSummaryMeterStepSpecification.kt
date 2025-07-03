package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.DistributionSummaryFailureConditionSpec
import io.qalipsis.api.meters.DistributionSummaryFailureConditionSpecImpl
import io.qalipsis.api.meters.specification.ComparableValueFailureSpecification
import io.qalipsis.api.meters.TimerFailureConditionSpec
import java.time.Duration

/**
 * @TODO
 * Specification for a [io.qalipsis.core.factory.steps.TimerStep].
 *
 * @author Francisca Eze
 */
@Introspected
data class DistributionSummaryMeterStepSpecification<INPUT>(
    val meterName: String,
    val percentiles: Map<Double, Duration>,
    val block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double,
) : AbstractStepSpecification<INPUT, INPUT, DistributionSummaryMeterStepSpecification<INPUT>>() {
    lateinit var checks: MutableList<ComparableValueFailureSpecification<DistributionSummary, Double>>

    fun shouldFailWhen(block: DistributionSummaryFailureConditionSpec.() -> INPUT): StepSpecification<*, INPUT, *> {
        val summaryFailureConditionSpecImpl = DistributionSummaryFailureConditionSpecImpl()
        summaryFailureConditionSpecImpl.block()
        checks = summaryFailureConditionSpecImpl.checks
        return this
    }

}

fun <INPUT> StepSpecification<*, INPUT, *>.summary(
    name: String,
    percentiles: Map<Double, Duration> = emptyMap(),
    block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double
): DistributionSummaryMeterStepSpecification<INPUT> {
    val step = DistributionSummaryMeterStepSpecification(name, percentiles, block)
    this.add(step)
    return step
}