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

package io.qalipsis.core.factory.steps.topicrelatedsteps

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
import io.qalipsis.api.runtime.Minion
import io.qalipsis.api.steps.Step
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.factory.context.StepContextImpl
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.factory.orchestration.Runner
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

        every { nextStep.name } returns "my-next-step"
        every { minionsKeeper.getSingletonMinion("my-scenario", "my-dag") } returns minion
        every { minion.id } returns "my-minion"
        val countDownLatch = SuspendedCountLatch(3)
        coEvery { runner.runMinion(any(), any(), any()) } coAnswers { countDownLatch.decrement() }

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
            runner.runMinion(refEq(minion), nextStep, capture(contexts))
        }

        assertThat(contexts).all {
            index(0).all {
                prop(StepContext<String, String>::campaignKey).isEqualTo("my-campaign")
                prop(StepContext<String, String>::scenarioName).isEqualTo("my-scenario")
                prop(StepContext<String, String>::previousStepName).isEqualTo("my-parent-step")
                prop(StepContext<String, String>::stepName).isEqualTo("my-next-step")
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

        every { nextStep.name } returns "my-next-step"
        every { minionsKeeper.getSingletonMinion("my-scenario", "my-dag") } returns minion
        every { minion.id } returns "my-minion"
        val countDownLatch = SuspendedCountLatch(3)
        coEvery { runner.runMinion(any(), any(), any()) } coAnswers { countDownLatch.decrement() }

        // when
        dataTransferTopic.produceValue("value-1")
        dataTransferTopic.produceValue("value-2")
        dataTransferTopic.produceValue("value-3")

        // then
        // Should not have sent any data.
        assertThat(countDownLatch.await(500, TimeUnit.MILLISECONDS)).isFalse()
        assertThat(countDownLatch.get()).isEqualTo(3L)
        coVerifyNever {
            runner.runMinion(any(), any(), any())
        }

        // when
        step.start(StepStartStopContext("my-campaign", "my-scenario", "my-dag", "my-next-step"))

        // then
        countDownLatch.await()
        step.stop(relaxedMockk())
        val contexts = mutableListOf<StepContext<String, String>>()
        coVerifyExactly(3) {
            runner.runMinion(refEq(minion), nextStep, capture(contexts))
        }
    }
}
