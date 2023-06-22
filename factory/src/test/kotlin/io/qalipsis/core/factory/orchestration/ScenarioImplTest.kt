/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.factory.orchestration

import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.testDag
import io.qalipsis.core.feedbacks.CampaignStartedForDagFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.atomic.AtomicInteger

/**
 *
 * @author Eric Jessé
 */
@WithMockk
internal class ScenarioImplTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var factoryChannel: FactoryChannel

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
    internal fun `should start all steps`() = testCoroutineDispatcher.runTest {
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
        scenario.start(mockk {
            every { campaignKey } returns "camp-1"
        })

        // then
        assertEquals(mockedSteps.size, calledStart.get())
        coVerifyOnce {
            factoryChannel.publishFeedback(
                CampaignStartedForDagFeedback(
                    "camp-1",
                    "my-scenario",
                    "dag-1",
                    FeedbackStatus.IN_PROGRESS
                )
            )
            factoryChannel.publishFeedback(
                CampaignStartedForDagFeedback(
                    "camp-1", "my-scenario", "dag-2", FeedbackStatus.IN_PROGRESS
                )
            )
            factoryChannel.publishFeedback(
                CampaignStartedForDagFeedback(
                    "camp-1", "my-scenario", "dag-1", FeedbackStatus.COMPLETED
                )
            )
            factoryChannel.publishFeedback(
                CampaignStartedForDagFeedback(
                    "camp-1", "my-scenario", "dag-2", FeedbackStatus.COMPLETED
                )
            )
        }
        mockedSteps.forEach { step ->
            val context = StepStartStopContext(
                campaignKey = "camp-1",
                scenarioName = "my-scenario",
                dagId = step.dagName,
                stepName = step.name
            )
            coVerifyOnce {
                step.start(eq(context))
            }
        }
        confirmVerified(factoryChannel)
    }

    @Test
    internal fun `should interrupt the start when a step fails`() = testCoroutineDispatcher.runTest {
        // given
        val mockedSteps = mutableListOf<StartableStoppableStep>()
        val scenario = buildDagsByScenario(mockedSteps)
        coEvery { mockedSteps[2].start(any()) } throws RuntimeException("this is the error")

        // when
        scenario.start(mockk {
            every { campaignKey } returns "camp-1"
        })

        // then
        coVerifyOnce {
            factoryChannel.publishFeedback(
                CampaignStartedForDagFeedback(
                    "camp-1", "my-scenario", "dag-1", FeedbackStatus.IN_PROGRESS
                )
            )
            factoryChannel.publishFeedback(
                CampaignStartedForDagFeedback(
                    "camp-1",
                    "my-scenario",
                    "dag-1",
                    FeedbackStatus.FAILED,
                    "The start of the DAG dag-1 failed: this is the error"
                )
            )
        }
        mockedSteps.subList(0, 2).forEach { step ->
            val context = StepStartStopContext(
                campaignKey = "camp-1",
                scenarioName = "my-scenario",
                dagId = step.dagName,
                stepName = step.name
            )
            coVerifyOnce {
                step.start(eq(context))
            }
        }
        mockedSteps.forEach { step ->
            val context = StepStartStopContext(
                campaignKey = "camp-1",
                scenarioName = "my-scenario",
                dagId = step.dagName,
                stepName = step.name
            )
            coVerifyOnce {
                step.stop(eq(context))
            }
        }
        confirmVerified(factoryChannel)
    }

    @Test
    internal fun `should stop all steps`() = testCoroutineDispatcher.runTest {
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
        scenario.stop(mockk {
            every { campaignKey } returns "camp-1"
        })

        // then
        assertEquals(mockedSteps.size, calledStop.get())
        mockedSteps.forEach { step ->
            val context = StepStartStopContext(
                campaignKey = "camp-1",
                scenarioName = "my-scenario",
                dagId = step.dagName,
                stepName = step.name
            )
            coVerifyOnce {
                step.stop(eq(context))
            }
        }
        confirmVerified(factoryChannel)
    }

    private fun buildDagsByScenario(
        mockedSteps: MutableList<StartableStoppableStep>
    ): ScenarioImpl {
        val scenarioImpl = ScenarioImpl(
            name = "my-scenario",
            executionProfile = relaxedMockk(),
            factoryChannel = factoryChannel
        )

        scenarioImpl.createIfAbsent("dag-1") {
            testDag(id = "dag-1") {
                addStep(spyk(StartableStoppableStep("dag-1", "step-1-1")).also {
                    mockedSteps.add(it)
                    it.new("step-1-2").also {
                        mockedSteps.add(it)
                    }
                    it.new("step-1-3").also {
                        mockedSteps.add(it)
                        it.new("step-1-4").also {
                            mockedSteps.add(it)
                        }
                    }
                })
            }
        }
        scenarioImpl.createIfAbsent("dag-2") {
            testDag(id = "dag-2") {
                addStep(spyk(StartableStoppableStep("dag-2", "step-2-1")).also {
                    mockedSteps.add(it)
                })
            }
        }
        return scenarioImpl
    }


    private data class StartableStoppableStep(val dagName: DirectedAcyclicGraphName, val stepName: StepName) :
        AbstractStep<Any?, Any?>(stepName, null) {

        override suspend fun execute(context: StepContext<Any?, Any?>) {
            // No-Op.
        }

        fun new(stepName: StepName): StartableStoppableStep {
            val nextStep = spyk(StartableStoppableStep(dagName, stepName))
            this.addNext(nextStep)
            return nextStep
        }
    }

}
