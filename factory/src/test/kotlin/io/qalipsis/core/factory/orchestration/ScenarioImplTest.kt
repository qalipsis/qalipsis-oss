package io.qalipsis.core.factory.orchestration

import io.mockk.coEvery
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.core.factory.testDag
import io.qalipsis.core.feedbacks.CampaignStartedForDagFeedback
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 *
 * @author Eric Jessé
 */
@WithMockk
internal class ScenarioImplTest {

    @RelaxedMockK
    lateinit var feedbackFactoryChannel: FeedbackFactoryChannel

    @Test
    internal fun `should destroy all steps`() {
        // given
        val mockedSteps = mutableListOf<StartableStoppableStep>()
        val scenario = buildDagsByScenario(mockedSteps)
        // when
        scenario.destroy()

        // then
        mockedSteps.forEach { step ->
            coVerifyOnce {
                step.destroy()
            }
        }
    }

    @Test
    internal fun `should start all steps`() {
        // given
        val mockedSteps = mutableListOf<StartableStoppableStep>()
        val scenario = buildDagsByScenario(mockedSteps)
        val calledStart = AtomicInteger(0)
        mockedSteps.forEach {
            coEvery { it.start(any()) } answers {
                calledStart.incrementAndGet()
                Unit
            }
        }

        // when
        scenario.start("camp-1")

        // then
        assertEquals(mockedSteps.size, calledStart.get())
        coVerifyOnce {
            feedbackFactoryChannel.publish(
                eq(
                    CampaignStartedForDagFeedback(
                        "camp-1", "my-scenario", "dag-1", FeedbackStatus.IN_PROGRESS
                    )
                )
            )
            feedbackFactoryChannel.publish(
                eq(
                    CampaignStartedForDagFeedback(
                        "camp-1", "my-scenario", "dag-2", FeedbackStatus.IN_PROGRESS
                    )
                )
            )
            feedbackFactoryChannel.publish(
                eq(
                    CampaignStartedForDagFeedback(
                        "camp-1", "my-scenario", "dag-1", FeedbackStatus.COMPLETED
                    )
                )
            )
            feedbackFactoryChannel.publish(
                eq(
                    CampaignStartedForDagFeedback(
                        "camp-1", "my-scenario", "dag-2", FeedbackStatus.COMPLETED
                    )
                )
            )
        }
        mockedSteps.forEach { step ->
            val context = StepStartStopContext(
                campaignId = "camp-1",
                scenarioId = "my-scenario",
                dagId = step.dagId,
                stepId = step.id
            )
            coVerifyOnce {
                step.start(eq(context))
            }
        }
    }

    @Test
    internal fun `should interrupt the start when a step is in timeout`() {
        // given
        val mockedSteps = mutableListOf<StartableStoppableStep>()
        val scenario = buildDagsByScenario(mockedSteps, Duration.ofMillis(100))
        coEvery { mockedSteps[2].start(any()) } coAnswers { delay(200) }

        // when
        scenario.start("camp-1")

        // then
        coVerifyOnce {
            feedbackFactoryChannel.publish(
                eq(
                    CampaignStartedForDagFeedback(
                        "camp-1", "my-scenario", "dag-1", FeedbackStatus.IN_PROGRESS
                    )
                )
            )
            feedbackFactoryChannel.publish(
                eq(
                    CampaignStartedForDagFeedback(
                        "camp-1",
                        "my-scenario",
                        "dag-1",
                        FeedbackStatus.FAILED,
                        "The start of the DAG dag-1 failed: Timed out waiting for 100 ms"
                    )
                )
            )
        }
        mockedSteps.subList(0, 2).forEach { step ->
            val context = StepStartStopContext(
                campaignId = "camp-1",
                scenarioId = "my-scenario",
                dagId = step.dagId,
                stepId = step.id
            )
            coVerifyOnce {
                step.start(eq(context))
            }
        }
        mockedSteps.forEach { step ->
            val context = StepStartStopContext(
                campaignId = "camp-1",
                scenarioId = "my-scenario",
                dagId = step.dagId,
                stepId = step.id
            )
            coVerifyOnce {
                step.stop(eq(context))
            }
        }
    }


    @Test
    internal fun `should interrupt the start when a step fails`() {
        // given
        val mockedSteps = mutableListOf<StartableStoppableStep>()
        val scenario = buildDagsByScenario(mockedSteps)
        coEvery { mockedSteps[2].start(any()) } throws RuntimeException("this is the error")

        // when
        scenario.start("camp-1")

        // then
        coVerifyOnce {
            feedbackFactoryChannel.publish(
                eq(
                    CampaignStartedForDagFeedback(
                        "camp-1", "my-scenario", "dag-1", FeedbackStatus.IN_PROGRESS
                    )
                )
            )
            feedbackFactoryChannel.publish(
                eq(
                    CampaignStartedForDagFeedback(
                        "camp-1",
                        "my-scenario",
                        "dag-1",
                        FeedbackStatus.FAILED,
                        "The start of the DAG dag-1 failed: this is the error"
                    )
                )
            )
        }
        mockedSteps.subList(0, 2).forEach { step ->
            val context = StepStartStopContext(
                campaignId = "camp-1",
                scenarioId = "my-scenario",
                dagId = step.dagId,
                stepId = step.id
            )
            coVerifyOnce {
                step.start(eq(context))
            }
        }
        mockedSteps.forEach { step ->
            val context = StepStartStopContext(
                campaignId = "camp-1",
                scenarioId = "my-scenario",
                dagId = step.dagId,
                stepId = step.id
            )
            coVerifyOnce {
                step.stop(eq(context))
            }
        }
    }

    @Test
    internal fun `should stop all steps`() {
        // given
        val mockedSteps = mutableListOf<StartableStoppableStep>()
        val scenario = buildDagsByScenario(mockedSteps)
        val calledStop = AtomicInteger(0)
        mockedSteps.forEach {
            coEvery { it.stop(any()) } answers {
                calledStop.incrementAndGet()
                Unit
            }
        }

        // when
        scenario.stop("camp-1")

        // then
        assertEquals(mockedSteps.size, calledStop.get())
        mockedSteps.forEach { step ->
            val context = StepStartStopContext(
                campaignId = "camp-1",
                scenarioId = "my-scenario",
                dagId = step.dagId,
                stepId = step.id
            )
            coVerifyOnce {
                step.stop(eq(context))
            }
        }
    }

    private fun buildDagsByScenario(
        mockedSteps: MutableList<StartableStoppableStep>,
        stepStartTimeout: Duration = Duration.ofSeconds(30)
    ): ScenarioImpl {
        val scenarioImpl = ScenarioImpl(
            id = "my-scenario",
            rampUpStrategy = relaxedMockk(),
            feedbackFactoryChannel = feedbackFactoryChannel,
            stepStartTimeout = stepStartTimeout
        )

        scenarioImpl.createIfAbsent("dag-1") {
            testDag(id = "dag-1") {
                addStep(spyk(StartableStoppableStep("dag-1")).also {
                    mockedSteps.add(it)
                    it.new().also {
                        mockedSteps.add(it)
                    }
                    it.new().also {
                        mockedSteps.add(it)
                        it.new().also {
                            mockedSteps.add(it)
                        }
                    }
                })
            }
        }
        scenarioImpl.createIfAbsent("dag-2") {
            testDag(id = "dag-2") {
                addStep(spyk(StartableStoppableStep("dag-2")).also {
                    mockedSteps.add(it)
                })
            }
        }
        return scenarioImpl
    }


    private data class StartableStoppableStep(val dagId: DirectedAcyclicGraphId) :
        AbstractStep<Any?, Any?>("", null) {

        override suspend fun execute(context: StepContext<Any?, Any?>) {
            // No-Op.
        }

        fun new(): StartableStoppableStep {
            val nextStep = spyk(StartableStoppableStep(dagId))
            this.addNext(nextStep)
            return nextStep
        }
    }

}
