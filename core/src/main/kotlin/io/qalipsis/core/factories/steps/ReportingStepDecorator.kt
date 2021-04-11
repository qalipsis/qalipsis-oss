package io.qalipsis.core.factories.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.factories.Minion
import io.qalipsis.api.report.CampaignStateKeeper
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepDecorator
import io.qalipsis.api.steps.StepExecutor
import java.util.concurrent.atomic.AtomicLong

/**
 * Decorator of a step, that records the successes and errors of the [decorated] step and reports the state
 * to teh [campaignStateKeeper].
 *
 * @author Eric Jessé
 */
internal class ReportingStepDecorator<I, O>(
    override val decorated: Step<I, O>,
    private val campaignStateKeeper: CampaignStateKeeper
) : Step<I, O>, StepExecutor, StepDecorator<I, O> {

    override val id: StepId
        get() = decorated.id

    override var retryPolicy: RetryPolicy? = decorated.retryPolicy

    override val next = decorated.next

    private val successCount = AtomicLong()

    private val errorCount = AtomicLong()

    override suspend fun start(context: StepStartStopContext) {
        successCount.set(0)
        errorCount.set(0)
        super<StepDecorator>.start(context)
    }

    override suspend fun stop(context: StepStartStopContext) {
        val result = """"Success: ${successCount.get()}, Execution errors: ${errorCount.get()}""""
        val severity = if (errorCount.get() > 0) {
            ReportMessageSeverity.ERROR
        } else {
            ReportMessageSeverity.INFO
        }
        campaignStateKeeper.put(context.campaignId, context.scenarioId, this.id, severity, result)
        log.info("Stopping the step ${this.id} for the campaign ${context.campaignId}: $result")
        super<StepDecorator>.stop(context)
    }

    override suspend fun execute(minion: Minion, context: StepContext<I, O>) {
        try {
            decorated.execute(minion, context)
            successCount.incrementAndGet()
        } catch (t: Throwable) {
            errorCount.incrementAndGet()
            throw t
        }
    }

    override suspend fun execute(context: StepContext<I, O>) {
        // This method should never be called.
        throw NotImplementedError()
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }

}
