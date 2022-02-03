package io.qalipsis.core.factory

import io.mockk.every
import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepId
import io.qalipsis.api.rampup.RampUpStrategy
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.runtime.DirectedAcyclicGraph
import io.qalipsis.api.runtime.Minion
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.api.steps.ErrorProcessingStep
import io.qalipsis.api.steps.StepDecorator
import io.qalipsis.core.factory.context.StepContextImpl
import io.qalipsis.core.factory.orchestration.ScenarioImpl
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 *
 * @author Eric Jessé
 */
@SuppressWarnings("kotlin:S107")
internal fun <IN : Any?, OUT : Any?> coreStepContext(
    input: IN? = null, outputChannel: SendChannel<StepContext.StepOutputRecord<OUT>?> = Channel(100),
    errors: MutableList<StepError> = mutableListOf(),
    minionId: MinionId = "my-minion",
    scenarioId: ScenarioId = "",
    directedAcyclicGraphId: DirectedAcyclicGraphId = "",
    parentStepId: StepId = "my-parent-step",
    stepId: StepId = "my-step", stepIterationIndex: Long = 0,
    attemptsAfterFailure: Long = 0, isExhausted: Boolean = false,
    completed: Boolean = false
): StepContextImpl<IN, OUT> {
    val inputChannel = Channel<IN>(1)
    runBlocking {
        input?.let {
            inputChannel.send(it)
        }
    }
    return StepContextImpl(
        inputChannel,
        outputChannel,
        errors,
        "",
        minionId,
        scenarioId,
        parentStepId,
        stepId,
        "",
        "",
        stepIterationIndex,
        isExhausted,
        isTail = false
    )
}

internal fun testScenario(
    id: ScenarioId = "my-scenario",
    rampUpStrategy: RampUpStrategy = relaxedMockk(),
    minionsCount: Int = 1,
    configure: suspend Scenario.() -> Unit = {}
): Scenario {
    val scenario = ScenarioImpl(
        id, rampUpStrategy = rampUpStrategy, minionsCount = minionsCount, feedbackFactoryChannel = relaxedMockk()
    )
    runBlocking {
        scenario.configure()
    }
    return scenario
}

internal fun testDag(
    id: DirectedAcyclicGraphId = "my-dag",
    scenario: Scenario = testScenario(),
    root: Boolean = false,
    isSingleton: Boolean = false,
    isUnderLoad: Boolean = false,
    configure: suspend DirectedAcyclicGraph.() -> Unit = {}
): DirectedAcyclicGraph {
    val dag = DirectedAcyclicGraph(
        id,
        scenario,
        isRoot = root,
        isSingleton = isSingleton,
        isUnderLoad = isUnderLoad,
        selectors = mutableMapOf()
    )
    runBlocking {
        dag.configure()
    }
    return dag
}

internal fun <O> DirectedAcyclicGraph.generate(
    id: String, output: O,
    retryPolicy: RetryPolicy? = null,
    generateException: Boolean = false
): TestStep<Unit, O> {
    val step = TestStep<Unit, O>(id, retryPolicy, output, generateException = generateException)
    val self = this
    runBlocking {
        self.addStep(step)
    }
    return step
}

internal fun <O> DirectedAcyclicGraph.noOutput(
    id: String,
    retryPolicy: RetryPolicy? = null,
    generateException: Boolean = false
): TestStep<Unit, O> {
    val step = TestStep<Unit, O>(id, retryPolicy, null, generateException = generateException)
    val self = this
    runBlocking {
        self.addStep(step)
    }
    return step
}


internal fun DirectedAcyclicGraph.steps(): Map<StepId, TestStep<*, *>> {
    val steps = mutableMapOf<StepId, TestStep<*, *>>()
    val rootStep = this.rootStep.forceGet() as TestStep<*, *>
    steps[rootStep.id] = rootStep
    rootStep.collectChildren(steps)
    return steps
}


internal open class TestStep<I, O>(
    id: String, retryPolicy: RetryPolicy? = null, private val output: O? = null,
    private val delay: Long? = null,
    private val generateException: Boolean = false
) : AbstractStep<I, O>(id, retryPolicy) {

    var received: I? = null

    private var executionCount = AtomicInteger(0)

    private var completionCount = AtomicInteger(0)

    private val singleCaptured = AtomicReference<StepContext<*, *>>()

    override suspend fun execute(context: StepContext<I, O>) {
        received = context.receive()
        doExecute(context)
    }

    protected suspend fun doExecute(context: StepContext<I, O>) {
        executionCount.incrementAndGet()
        Assertions.assertEquals("my-scenario", context.scenarioId)
        singleCaptured.set(context)

        delay?.let {
            delay(it)
        }

        if (generateException) {
            throw RuntimeException()
        } else if (output != null) {
            context.send(output)
        }
    }

    override suspend fun complete(completionContext: CompletionContext) {
        completionCount.incrementAndGet()
    }

    /**
     * Creates a forwarder step as next.
     */
    fun forward(id: String): TestStep<O, O> {
        val step = ForwarderTestStep<O>(id)
        this.addNext(step)
        return step
    }

    /**
     * Creates a forwarder step executed [repetitions] times.
     */
    fun repeated(id: String, repetitions: Int): TestStep<O, O> {
        val step = ForwarderTestStep<O>(id, repetitions)
        this.addNext(step)
        return step
    }

    /**
     * Creates a forwarder step executed [repetitions] times.
     */
    fun blackhole(id: String): TestStep<O, Unit> {
        val step = BlackholeTestStep<O>(id)
        this.addNext(step)
        return step
    }

    /**
     * Creates a delayed forwarder step as next.
     */
    fun delayed(id: String, output: O, delay: Long): TestStep<Unit, O> {
        val step = TestStep<Unit, O>(id, output = output, delay = delay)
        this.addNext(step)
        return step
    }

    /**
     * Creates a step generating an error as next.
     */
    fun <O2> errorGenerator(id: String): TestStep<O, O> {
        val step = TestStep<O, O>(id, generateException = true)
        this.addNext(step)
        return step
    }

    /**
     * Creates an error processing step as next.
     */
    fun processError(id: String): TestStep<O, O> {
        val step = ErrorProcessingTestStep<O>(id)
        this.addNext(step)
        return step
    }

    /**
     * Creates an error processing step as next.
     */
    fun decoratedProcessError(id: String): StepDecorator<O, O> {
        val step = ErrorProcessingTestStep<O>(id)
        this.addNext(step)
        return relaxedMockk {
            every { decorated } returns relaxedMockk<StepDecorator<O, O>> {
                every { decorated } returns step
            }
            every { addNext(any()) } answers { step.addNext(firstArg()) }
        }
    }

    /**
     * Creates a recovery step as next.
     */
    fun <O2> recoverError(id: String, output: O2): TestStep<O, O2> {
        val step = RecoveryTestStep<O, O2>(id, output)
        this.addNext(step)
        return step
    }

    /**
     * Creates several steps at once.
     */
    fun all(block: TestStep<I, O>.() -> Unit) {
        this.block()
    }

    fun assertHasParent(expectedParentStepId: StepId?) {
        Assertions.assertEquals(expectedParentStepId, singleCaptured.get().previousStepId)
    }

    fun assertExecuted() {
        Assertions.assertTrue(executionCount.get() > 0, "step $id should have been executed")
    }

    fun assertNotExecuted() {
        assertExecutionCount(0)
    }

    fun assertExecutionCount(count: Int) {
        Assertions.assertEquals(
            count,
            executionCount.get(),
            "step $id should have been executed $count time(s) but was ${executionCount.get()} time(s)"
        )
    }

    fun assertCompleted() {
        Assertions.assertTrue(completionCount.get() > 0, "step $id should have been completed")
    }

    fun assertNotCompleted() {
        assertCompletionCount(0)
    }

    fun assertCompletionCount(count: Int) {
        Assertions.assertEquals(
            count,
            completionCount.get(),
            "step $id should have been completed $count time(s) but was ${completionCount.get()} time(s)"
        )
    }

    fun assertExhaustedContext() {
        Assertions.assertTrue(singleCaptured.get().isExhausted, "step $id should have received an exhausted context")
    }

    fun assertNotExhaustedContext() {
        Assertions.assertFalse(
            singleCaptured.get().isExhausted,
            "step $id should have not received an exhausted context"
        )
    }

    fun collectChildren(steps: MutableMap<StepId, TestStep<*, *>>) {
        next.forEach {
            val step = it as TestStep<*, *>
            steps[it.id] = step
            step.collectChildren(steps)
        }
    }
}

internal open class ForwarderTestStep<I>(id: String, private val repetitions: Int = 1) : TestStep<I, I>(id) {

    /**
     * Forward the input to the output additionally to what [TestStep] does.
     */
    override suspend fun execute(context: StepContext<I, I>) {
        if (context.isExhausted) {
            doExecute(context)
        } else {
            val isContextATail = context.isTail
            super.execute(context)
            context.isTail = false
            repeat(repetitions - 1) {
                context.send(received!!)
            }
            context.isTail = isContextATail
            context.send(received!!)
        }
    }
}

internal class BlackholeTestStep<I>(id: String) : TestStep<I, Unit>(id) {

    /**
     * Does not provide any output.
     */
    override suspend fun execute(context: StepContext<I, Unit>) {
        if (context.isExhausted) {
            doExecute(context)
        } else {
            super.execute(context)
        }
    }
}

internal open class ErrorProcessingTestStep<I>(id: String) : ForwarderTestStep<I>(id),
    ErrorProcessingStep<I, I> {

    override suspend fun execute(minion: Minion, context: StepContext<I, I>) {
        super<ForwarderTestStep>.execute(context)
    }
}

internal class RecoveryTestStep<I, O>(id: String, output: O) : TestStep<I, O>(id, output = output),
    ErrorProcessingStep<I, O> {

    /**
     * Recovers the context additionally to what [TestStep] does.
     */
    override suspend fun execute(minion: Minion, context: StepContext<I, O>) {
        context.isExhausted = false
        doExecute(context)
    }
}
