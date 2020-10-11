package io.qalipsis.core.factories.orchestration

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.DirectedAcyclicGraph
import io.qalipsis.api.orchestration.factories.Minion
import io.qalipsis.api.steps.ErrorProcessingStep
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepExecutor
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.factories.context.StepContextBuilder
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.time.Duration
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
        private val meterRegistry: MeterRegistry
) : StepExecutor, Runner {

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
        val ctx =
            StepContext<Unit, Any>(campaignId = minion.campaignId, minionId = minion.id,
                    scenarioId = dag.scenario.id,
                    directedAcyclicGraphId = dag.id, stepId = step.id, creation = creationTimestamp)
        (ctx.input as Channel<Unit>).send(Unit)

        @Suppress("UNCHECKED_CAST")
        launch(minion, step, ctx)

    }

    override suspend fun launch(minion: Minion, step: Step<*, *>, ctx: StepContext<*, *>) {
        minion.start()
        runningMinionsGauge.incrementAndGet()
        minion.onComplete { runningMinionsGauge.decrementAndGet() }
        log.trace("Running minion ${minion.id}")

        minion.attach(GlobalScope.launch {
            executeStepRecursively(minion, step, ctx)
        })
    }

    /**
     * Execute a single step onto the specified context and triggers the next steps asynchronously in different coroutines.
     */
    private suspend fun executeStepRecursively(minion: Minion, step: Step<*, *>, ctx: StepContext<*, *>) {
        log.trace("Executing step ${step.id} with minion ${minion.id} on context $ctx")
        // Asynchronously read the output to trigger the next steps.
        if (step.next.isNotEmpty()) {
            // The output is read asynchronously to release the current coroutine and let the step execute.
            minion.attach(GlobalScope.launch {
                var errorOfContextProcessed = false
                for (outputRecord in ctx.output as Channel<*>) {
                    step.next.forEach { nextStep ->
                        if (ctx.isExhausted) {
                            errorOfContextProcessed = true
                        }

                        // Each next step is executed in its individual coroutine and with a dedicated context.
                        @Suppress("UNCHECKED_CAST")
                        val nextContext =
                            StepContextBuilder.next<Any?, Any?, Any?>(outputRecord, ctx as StepContext<Any?, Any?>,
                                    nextStep.id)
                        minion.attach(launch {
                            executeStepRecursively(minion, nextStep, nextContext)
                        })
                    }
                }
                // If the context is exhausted, it only ensures that the error processing steps are executed.
                if (!errorOfContextProcessed && ctx.isExhausted) {
                    step.next.forEach { nextStep ->
                        val nextContext = StepContextBuilder.next(ctx, nextStep.id)
                        minion.attach(launch {
                            executeStepRecursively(minion, nextStep, nextContext)
                        })
                    }
                }
            })
        } else {
            ctx.isCompleted = true
        }

        executeSingleStep(step, ctx)
    }

    private suspend fun executeSingleStep(step: Step<*, *>, ctx: StepContext<*, *>) {
        if (!ctx.isExhausted || step is ErrorProcessingStep) {
            eventsLogger.info("step-${step.id}-started", tagsSupplier = { ctx.toEventTags() })

            runningStepsGauge.incrementAndGet()
            val start = System.nanoTime()
            try {
                @Suppress("UNCHECKED_CAST")
                executeStep(step as Step<Any?, Any?>, ctx as StepContext<Any?, Any?>)
                Duration.ofNanos(System.nanoTime() - start).let { duration ->
                    eventsLogger.info("step-${step.id}-completed", tagsSupplier = { ctx.toEventTags() })
                    meterRegistry.timer("step-execution", "step", step.id, "status", "completed").record(duration)
                }
            } catch (t: Throwable) {
                Duration.ofNanos(System.nanoTime() - start).let { duration ->
                    eventsLogger.warn("step-${step.id}-failed", t, tagsSupplier = { ctx.toEventTags() })
                    meterRegistry.timer("step-execution", "step", step.id, "status", "failed").record(duration)
                }
                ctx.errors.add(StepError(t))
                log.warn(
                        "step $step completed with an exception for the minion ${ctx.minionId}, the context is marked as exhausted",
                        t
                )
                ctx.isExhausted = true
            }
            runningStepsGauge.decrementAndGet()
            executedStepCounter.increment()
        }
        // Once the step was executed, the channels can be closed if not done in the step itself.
        ctx.output.close()
        (ctx.input as Channel<*>).close()
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
