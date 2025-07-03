package io.qalipsis.core.factory.steps.meter

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.Throughput
import io.qalipsis.api.meters.specification.ComparableValueFailureSpecification
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import java.time.Duration

/**
 * @TODO
 *
 * @author Francisca Eze
 */
internal class ThroughputMeterStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val meterName: String,
    val percentiles: Map<Double, Duration>,
    val block: (stepContext: StepContext<I, I>, input: I) -> Double,
    val specifications: List<ComparableValueFailureSpecification<Throughput, Double>>,
    private val campaignMeterRegistry: CampaignMeterRegistry,
    //@TODO it exists in a different package
//    val campaignReportStateKeeper: CampaignReportStateKeeper
) : AbstractStep<I, I>(id, retryPolicy) {

    private lateinit var meter: Throughput
    private val converter = ValueCheckConverter()

    override suspend fun start(context: StepStartStopContext) {
        meter = campaignMeterRegistry.throughput(
            scenarioName = context.scenarioName,
            stepName = context.stepName,
            name = meterName,
            tags = context.toMetersTags(),
            //@TODO to be fixed
//            percentiles = percentiles
        )
    }

    override suspend fun execute(context: StepContext<I, I>) {
        try {
            val input = context.receive()
            val valueToRecord = block(context, input)
            meter.record(valueToRecord)

            //TODO MAke ASYNC
            if (specifications.isNotEmpty()) {
                val exceptions = specifications.map { spec ->
                    converter.convert(spec, meter)
                }
                if (exceptions.isNotEmpty()) {
                    // TODO Find a way to report the failure state with the campaignStateKeeper.reportException()
                } else {
                    // TODO Remove failure states.
                }
            }
            context.send(input)
        } catch (e: Exception) {
            log.error(e) { e.message }
            throw e
        }
    }

    override suspend fun stop(context: StepStartStopContext) {
        //@TODO Meter values reported into the console.
        super.stop(context)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}