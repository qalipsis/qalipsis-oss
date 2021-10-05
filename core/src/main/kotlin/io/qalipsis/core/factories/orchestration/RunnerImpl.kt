package io.qalipsis.core.factories.orchestration

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.DirectedAcyclicGraph
import io.qalipsis.api.orchestration.factories.Minion
import io.qalipsis.api.report.CampaignStateKeeper
import io.qalipsis.api.steps.ErrorProcessingStep
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepDecorator
import io.qalipsis.api.steps.StepExecutor
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.exceptions.StepExecutionException
import io.qalipsis.core.factories.context.StepContextBuilder
import io.qalipsis.core.factories.context.StepContextImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import org.slf4j.MDC
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Singleton

/**
 * The Runner is the masterpiece of the factory and drives the minions to execute directed acyclic graphs (aka DAGs)
 * as requested by the head.
 *
 * It creates the Minions and assign them a list of DAGs, which are not directly connected altogether.
 *
 * The DAGs generally have input and output which are connected via messaging to the other
 * DAGs of the same minions running on different factories.
 *
 * @author Eric Jessé
 */
@Singleton
internal class RunnerImpl(
    private val eventsLogger: EventsLogger,
    private val meterRegistry: MeterRegistry,
    private val campaignStateKeeper: CampaignStateKeeper
) : StepExecutor, Runner {

    /**
     * Scope to launch the coroutines for the steps.
     */
    private val executionScope: CoroutineScope = GlobalScope

    /**
     * Map keeping the type of the steps.
     */
    private val stepTypes = ConcurrentHashMap<String, String>()

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private val idleMinionsGauge: AtomicInteger = meterRegistry.gauge("idle-minions", AtomicInteger())

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private val runningMinionsGauge: AtomicInteger = meterRegistry.gauge("running-minions", AtomicInteger())

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private val runningStepsGauge: AtomicInteger = meterRegistry.gauge("running-steps", AtomicInteger())

    private val executedStepCounter = meterRegistry.counter("executed-steps")

    @LogInput
    override suspend fun run(minion: Minion, dag: DirectedAcyclicGraph) {
        idleMinionsGauge.incrementAndGet()
        minion.waitForStart()
        idleMinionsGauge.decrementAndGet()

        val step = dag.rootStep.get()
        val creationTimestamp = System.currentTimeMillis()
        val ctx = StepContextImpl<Unit, Any>(
            campaignId = minion.campaignId, minionId = minion.id,
            scenarioId = dag.scenario.id,
            directedAcyclicGraphId = dag.id, stepId = step.id, creation = creationTimestamp
        )
        (ctx.input as Channel<Unit>).send(Unit)

        @Suppress("UNCHECKED_CAST")
        launch(minion, step, ctx)
    }

    @LogInput
    override suspend fun launch(
        minion: Minion, step: Step<*, *>, ctx: StepContext<*, *>,
        jobsCounter: SuspendedCountLatch?,
        consumer: (suspend (ctx: StepContext<*, *>) -> Unit)?
    ) {
        configureMdcContext(minion)

        minion.start()
        runningMinionsGauge.incrementAndGet()
        minion.onComplete { runningMinionsGauge.decrementAndGet() }

        log.trace { "Running minion" }

        MDC.put("step", step.id)
        minion.launch(executionScope, countLatch = jobsCounter) {
            log.trace { "Starting the execution of a subtree of tasks for the minion" }
            doExecute(this, minion, step, ctx, jobsCounter, consumer)
            log.trace { "Execution of the subtree of tasks is completed for the minion (1/2)" }
        }
    }

    @LogInputAndOutput
    override suspend fun execute(
        minion: Minion, step: Step<*, *>, ctx: StepContext<*, *>,
        consumer: (suspend (ctx: StepContext<*, *>) -> Unit)?
    ): Job? {
        configureMdcContext(minion)

        MDC.put("step", step.id)
        return try {
            minion.launch(executionScope) {
                doExecute(this, minion, step, ctx, null, consumer)
            }
        } finally {
            MDC.clear()
        }
    }

    /**
     * Configures the [MDC] context for the execution of the minion.
     */
    private fun configureMdcContext(minion: Minion) {
        minion.completeMdcContext()
        MDC.put("step", "_runner")
    }

    private suspend fun doExecute(
        minionScope: CoroutineScope,
        minion: Minion, step: Step<*, *>, ctx: StepContext<*, *>,
        jobsCounter: SuspendedCountLatch?,
        consumer: (suspend (ctx: StepContext<*, *>) -> Unit)?
    ) {
        log.trace { "Executing step with context $ctx" }
        // Asynchronously read the output to trigger the next steps.
        if (step.next.isNotEmpty()) {
            // The output is read asynchronously to release the current coroutine and let the step execute.
            minion.launch(minionScope, countLatch = jobsCounter) {
                log.trace { "Scheduling next steps" }
                scheduleNextSteps(minionScope, ctx, step, minion, jobsCounter, consumer)
            }
        } else {
            ctx.isCompleted = true
            // If the step is the latest one of the chain and a completion operation is provided, the output channel
            // is provided to completion operation.
            consumer?.let { completionConsumer ->
                log.trace { "Launching the consumer for the latest step of the graph" }
                MDC.put("step", "_completion")
                minion.launch(minionScope, countLatch = jobsCounter) { completionConsumer(ctx) }
            }
        }

        executeSingleStep(minion, step, ctx)
    }

    /**
     * Schedules the jobs to execute the next steps or the error processing steps if the context is exhausted.
     */
    private suspend fun scheduleNextSteps(
        minionScope: CoroutineScope,
        ctx: StepContext<*, *>, step: Step<*, *>,
        minion: Minion, jobsCounter: SuspendedCountLatch?,
        consumer: (suspend (ctx: StepContext<*, *>) -> Unit)?
    ) {
        var errorOfContextProcessed = false
        var hasOutput = false
        log.trace { "Waiting for the output of the step ${step.id}" }
        for (outputRecord in (ctx as StepContextImpl<*, *>).output as Channel<*>) {
            hasOutput = true
            step.next.forEach { nextStep ->

                if (ctx.isExhausted) {
                    errorOfContextProcessed = true
                }
                // Each next step is executed in its individual coroutine and with a dedicated context.
                @Suppress("UNCHECKED_CAST")
                val nextContext =
                    StepContextBuilder.next<Any?, Any?, Any?>(outputRecord, ctx as StepContext<Any?, Any?>, nextStep.id)

                MDC.put("step", nextStep.id)
                minion.launch(minionScope, countLatch = jobsCounter) {
                    log.trace { "Launching the coroutine for the next step ${nextStep.id}" }
                    doExecute(minionScope, minion, nextStep, nextContext, jobsCounter, consumer)
                }
            }
        }
        log.trace { "Output of the step ${step.id} was fully consumed" }

        // If the context is exhausted, it only ensures that the error processing steps are executed.
        if (!errorOfContextProcessed && ctx.isExhausted) {
            step.next.forEach { nextStep ->
                val nextContext = StepContextBuilder.next(ctx, nextStep.id)
                MDC.put("step", nextStep.id)
                minion.launch(minionScope, countLatch = jobsCounter) {
                    log.trace { "Launching the coroutine for the next error processing step ${nextStep.id}" }
                    doExecute(minionScope, minion, nextStep, nextContext, jobsCounter, consumer)
                }
            }
        } else if (!hasOutput) {
            ctx.isCompleted = true
            consumer?.let { completionConsumer ->
                log.trace { "Launching the consumer for a step without output" }
                MDC.put("step", "_completion")
                minion.launch(minionScope, countLatch = jobsCounter) { completionConsumer(ctx) }
            }
        }
    }

    private suspend fun executeSingleStep(minion: Minion, step: Step<*, *>, ctx: StepContext<*, *>) {
        if (!ctx.isExhausted || isErrorProcessingStep(step)) {
            log.trace { "Performing the execution of the step..." }
            ctx.stepType = stepTypes.computeIfAbsent(step.id) { getStepType(step) }
            eventsLogger.info("step.execution.started", tagsSupplier = { ctx.toEventTags() })
            runningStepsGauge.incrementAndGet()
            val start = System.nanoTime()
            try {
                @Suppress("UNCHECKED_CAST")
                executeStep(minion, step as Step<Any?, Any?>, ctx as StepContext<Any?, Any?>)
                Duration.ofNanos(System.nanoTime() - start).let { duration ->
                    eventsLogger.info("step.execution.complete", tagsSupplier = { ctx.toEventTags() })
                    meterRegistry.timer("step-execution", "step", step.id, "status", "completed").record(duration)
                }
                campaignStateKeeper.recordSuccessfulStepExecution(ctx.campaignId, minion.scenarioId, step.id)
                log.trace { "Step completed with success" }
            } catch (t: Throwable) {
                val duration = Duration.ofNanos(System.nanoTime() - start)
                campaignStateKeeper.recordFailedStepExecution(ctx.campaignId, minion.scenarioId, step.id)
                val cause = getFailureCause(t, ctx, step)
                eventsLogger.warn("step.execution.failed", cause, tagsSupplier = { ctx.toEventTags() })
                meterRegistry.timer("step-execution", "step", step.id, "status", "failed").record(duration)
                log.warn(t) { "Step completed with an exception, the context is marked as exhausted" }
                ctx.isExhausted = true
            }
            runningStepsGauge.decrementAndGet()
            executedStepCounter.increment()
        } else {
            log.trace { "The step will not be executed on minion because the context is exhausted and the step cannot process it" }
        }
        // Once the step was executed, the channels can be closed if not done in the step itself.
        ((ctx as StepContextImpl<*, *>).input as Channel<*>).close()
        ctx.output.close()
    }

    /**
     * Extracts the actual cause of the step execution failure.
     */
    private fun getFailureCause(t: Throwable, ctx: StepContext<*, *>, step: Step<*, *>) = when {
        t !is StepExecutionException -> {
            ctx.addError(StepError(t, step.id))
            t
        }
        t.cause != null -> {
            ctx.addError(StepError(t.cause!!, step.id))
            t.cause!!
        }
        else -> ctx.errors.lastOrNull()?.message
    }

    private fun isErrorProcessingStep(step: Step<*, *>): Boolean {
        return if (step is StepDecorator<*, *>) {
            isErrorProcessingStep(step.decorated)
        } else {
            step is ErrorProcessingStep
        }
    }

    private fun getStepType(step: Step<*, *>): String {
        return if (step is StepDecorator<*, *>) {
            getStepType(step.decorated)
        } else {
            step::class.simpleName!!.substringAfterLast(".").substringBefore("Step")
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
