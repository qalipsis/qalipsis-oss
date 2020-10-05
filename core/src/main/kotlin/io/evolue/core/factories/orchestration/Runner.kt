package io.evolue.core.factories.orchestration

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepError
import io.evolue.api.events.EventsLogger
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.orchestration.DirectedAcyclicGraph
import io.evolue.api.steps.ErrorProcessingStep
import io.evolue.api.steps.Step
import io.evolue.api.steps.StepExecutor
import io.evolue.core.annotations.LogInput
import io.evolue.core.factories.context.StepContextBuilder
import io.micrometer.core.instrument.MeterRegistry
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
internal class Runner(
        private val eventsLogger: EventsLogger,
        private val meterRegistry: MeterRegistry
) : StepExecutor {

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private val idleMinionsGauge: AtomicInteger = meterRegistry.gauge("idle-minions", AtomicInteger(0))

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private val runningMinionsGauge: AtomicInteger = meterRegistry.gauge("running-minions", AtomicInteger(0))

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private val runningStepsGauge: AtomicInteger = meterRegistry.gauge("running-steps", AtomicInteger(0))

    private val executedStepCounter = meterRegistry.counter("executed-steps")

    /**
     * Execute the dag onto the specified minion.
     */
    @LogInput
    suspend fun run(minionImpl: MinionImpl, dag: DirectedAcyclicGraph) {
        idleMinionsGauge.incrementAndGet()
        minionImpl.waitForStart()
        idleMinionsGauge.decrementAndGet()
        runningMinionsGauge.incrementAndGet()
        minionImpl.onComplete { runningMinionsGauge.decrementAndGet() }
        log.trace("Running minion ${minionImpl.id} for DAG ${dag.id}")

        val creationTimestamp = System.currentTimeMillis()
        val step = dag.rootStep.get()
        val ctx =
            StepContext<Unit, Any>(campaignId = minionImpl.campaignId, minionId = minionImpl.id,
                    scenarioId = dag.scenario.id,
                    directedAcyclicGraphId = dag.id, stepId = step.id, creation = creationTimestamp)
        (ctx.input as Channel<Unit>).send(Unit)
        minionImpl.attach(GlobalScope.launch {
            @Suppress("UNCHECKED_CAST")
            executeStepRecursively(minionImpl, step as Step<Any?, Any?>, ctx as StepContext<Any?, Any?>)
        })
    }

    /**
     * Execute a single step onto the specified context and triggers the next steps asynchronously in different coroutines.
     */
    private suspend fun executeStepRecursively(minionImpl: MinionImpl, step: Step<Any?, Any?>,
                                               ctx: StepContext<Any?, Any?>) {
        log.trace("Executing step ${step.id} with minion ${minionImpl.id} on context $ctx")
        // Asynchronously read the output to trigger the next steps.
        if (step.next.isNotEmpty()) {
            // The output is read asynchronously to release the current coroutine and let the step execute.
            minionImpl.attach(GlobalScope.launch {
                var errorOfContextProcessed = false
                for (outputRecord in ctx.output as Channel) {
                    step.next.forEach { nextStep ->
                        if (ctx.isExhausted) {
                            errorOfContextProcessed = true
                        }

                        // Each next step is executed in its individual coroutine and with a dedicated context.
                        val nextContext = StepContextBuilder.next<Any?, Any?, Any?>(outputRecord, ctx, nextStep.id)
                        minionImpl.attach(launch {
                            @Suppress("UNCHECKED_CAST")
                            executeStepRecursively(minionImpl, nextStep as Step<Any?, Any?>, nextContext)
                        })
                    }
                }
                // If the context is exhausted, it only ensures that the error processing steps are executed.
                if (!errorOfContextProcessed && ctx.isExhausted) {
                    step.next.forEach { nextStep ->
                        val nextContext = StepContextBuilder.next(ctx, nextStep.id)
                        minionImpl.attach(launch {
                            @Suppress("UNCHECKED_CAST")
                            executeStepRecursively(minionImpl, nextStep as Step<Any?, Any?>,
                                    nextContext as StepContext<Any?, Any?>)
                        })
                    }
                }
            })
        } else {
            ctx.isCompleted = true
        }

        executeSingleStep(step, ctx)
    }

    private suspend fun executeSingleStep(step: Step<Any?, Any?>, ctx: StepContext<Any?, Any?>) {
        if (!ctx.isExhausted || step is ErrorProcessingStep) {
            eventsLogger.info("step-${step.id}-started", tagsSupplier = { ctx.toEventTags() })

            runningStepsGauge.incrementAndGet()
            val start = System.nanoTime()
            try {
                executeStep(step, ctx)
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
                        "step ${step} completed with an exception for the minion ${ctx.minionId}, the context is marked as exhausted",
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
