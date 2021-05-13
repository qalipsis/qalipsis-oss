package io.qalipsis.core.factories.steps.topicrelatedsteps

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.messaging.unicastTopic
import io.qalipsis.api.orchestration.factories.Minion
import io.qalipsis.api.orchestration.factories.MinionsKeeper
import io.qalipsis.api.steps.Step
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.factories.context.StepContextImpl
import io.qalipsis.core.factories.orchestration.Runner
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyExactly
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

/**
 *
 * @author Eric Jessé
 */
@WithMockk
internal class TopicDataPushStepTest {

    @RelaxedMockK
    lateinit var nextStep: Step<String, *>

    @RelaxedMockK
    lateinit var minionsKeeper: MinionsKeeper

    @RelaxedMockK
    lateinit var runner: Runner

    @RelaxedMockK
    lateinit var minion: Minion

    @Test
    @Timeout(5)
    fun `should read all the data from topic and pass it to next step`() = runBlockingTest {
        // given
        val dataTransferTopic: Topic<String> = unicastTopic()
        val step = TopicDataPushStep("my-step", "my-parent-step", dataTransferTopic, coroutineScope = this)
        step.next.add(nextStep)
        step.minionsKeeper = minionsKeeper
        step.runner = runner

        every { nextStep.id } returns "my-next-step"
        every { minionsKeeper.getSingletonMinion(eq("my-dag")) } returns minion
        every { minion.id } returns "my-minion"
        val countDownLatch = SuspendedCountLatch(3)
        coEvery { runner.launch(any(), any(), any()) } coAnswers { countDownLatch.decrement() }

        // when
        step.start(StepStartStopContext("my-campaign", "my-scenario", "my-dag", "my-next-step"))
        dataTransferTopic.produceValue("value-1")
        dataTransferTopic.produceValue("value-2")
        dataTransferTopic.produceValue("value-3")

        // then
        countDownLatch.await()
        step.stop(relaxedMockk())
        val contexts = mutableListOf<StepContext<String, String>>()
        coVerifyExactly(3) {
            runner.launch(refEq(minion), nextStep, capture(contexts))
        }

        assertThat(contexts).all {
            index(0).all {
                prop(StepContext<String, String>::campaignId).isEqualTo("my-campaign")
                prop(StepContext<String, String>::scenarioId).isEqualTo("my-scenario")
                prop(StepContext<String, String>::directedAcyclicGraphId).isEqualTo("my-dag")
                prop(StepContext<String, String>::parentStepId).isEqualTo("my-parent-step")
                prop(StepContext<String, String>::stepId).isEqualTo("my-next-step")
                prop(StepContext<String, String>::minionId).isEqualTo("my-minion")
                prop(StepContext<String, String>::isTail).isFalse()
                transform { (it as StepContextImpl).output.isClosedForSend }.isFalse()
            }
        }
        // Verifies the values sent into the context for the next step.
        val emittedValues = contexts.map { it.receive() }
        assertThat(emittedValues).containsExactly("value-1", "value-2", "value-3")
    }

    @Test
    @Timeout(5)
    internal fun `should only push data when running`() = runBlockingTest {
        // given
        val dataTransferTopic: Topic<String> = unicastTopic()
        val step = TopicDataPushStep("my-step", "my-parent-step", dataTransferTopic, coroutineScope = this)
        step.next.add(nextStep)
        step.minionsKeeper = minionsKeeper
        step.runner = runner

        every { nextStep.id } returns "my-next-step"
        every { minionsKeeper.getSingletonMinion(eq("my-dag")) } returns minion
        every { minion.id } returns "my-minion"
        val countDownLatch = SuspendedCountLatch(3)
        coEvery { runner.launch(any(), any(), any()) } coAnswers { countDownLatch.decrement() }

        // when
        dataTransferTopic.produceValue("value-1")
        dataTransferTopic.produceValue("value-2")
        dataTransferTopic.produceValue("value-3")

        // then
        // Should not have sent any data.
        assertThat(countDownLatch.await(500, TimeUnit.MILLISECONDS)).isFalse()
        assertThat(countDownLatch.get()).isEqualTo(3L)
        coVerifyNever {
            runner.launch(any(), any(), any())
        }

        // when
        step.start(StepStartStopContext("my-campaign", "my-scenario", "my-dag", "my-next-step"))

        // then
        countDownLatch.await()
        step.stop(relaxedMockk())
        val contexts = mutableListOf<StepContext<String, String>>()
        coVerifyExactly(3) {
            runner.launch(refEq(minion), nextStep, capture(contexts))
        }
    }
}
