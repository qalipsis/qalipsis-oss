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

package io.qalipsis.core.factory.steps

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.DefaultCompletionContext
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

@WithMockk
internal class DistributedDagTransitionStepTest {

    @field:RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @RelaxedMockK
    private lateinit var localAssignmentStore: LocalAssignmentStore

    @RelaxedMockK
    private lateinit var contextForwarder: ContextForwarder

    @Test
    @Timeout(1)
    internal fun `should execute pipeline operation when the next DAG is local`() = testCoroutineDispatcher.runTest {
        // given
        val step = DistributedDagTransitionStep<Int>(
            "",
            "this-is-my-dag",
            "this-is-the-next-dag",
            factoryCampaignManager,
            localAssignmentStore,
            contextForwarder,
            false
        )
        val ctx = StepTestHelper.createStepContext<Int, Int>(
            input = 1,
            campaignKey = "my-campaign",
            scenarioName = "my-scenario",
            minionId = "my-minion"
        )
        every { localAssignmentStore.isLocal("my-scenario", "my-minion", "this-is-the-next-dag") } returns true

        // when
        step.execute(ctx)

        //then
        assertThat(ctx.hasInput).isFalse()
        assertThat(ctx.generatedOutput).isTrue()
        val output = ctx.consumeOutputValue()
        Assertions.assertEquals(1, output)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

        coVerifyOnce {
            localAssignmentStore.isLocal("my-scenario", "my-minion", "this-is-the-next-dag")
        }
        confirmVerified(factoryCampaignManager, contextForwarder)
    }

    @Test
    @Timeout(1)
    internal fun `should forward the step execution when the next DAG is remote`() = testCoroutineDispatcher.run {
        // given
        val step = DistributedDagTransitionStep<Int>(
            "",
            "this-is-my-dag",
            "this-is-the-next-dag",
            factoryCampaignManager,
            localAssignmentStore,
            contextForwarder,
            false
        )
        val ctx = StepTestHelper.createStepContext<Int, Int>(
            input = 1,
            campaignKey = "my-campaign",
            scenarioName = "my-scenario",
            minionId = "my-minion"
        )
        every { localAssignmentStore.isLocal("my-scenario", "my-minion", "this-is-the-next-dag") } returns false

        // when
        step.execute(ctx)

        //then
        assertThat(ctx.hasInput).isTrue()
        assertThat(ctx.generatedOutput).isFalse()
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

        coVerifyOnce {
            localAssignmentStore.isLocal("my-scenario", "my-minion", "this-is-the-next-dag")
            contextForwarder.forward(refEq(ctx), listOf("this-is-the-next-dag"))
        }
        confirmVerified(factoryCampaignManager, contextForwarder)
    }

    @Test
    @Timeout(1)
    internal fun `should complete pipeline operation when the next DAG is local`() = testCoroutineDispatcher.runTest {
        // given
        val step = DistributedDagTransitionStep<Int>(
            "",
            "this-is-my-dag",
            "this-is-the-next-dag",
            factoryCampaignManager,
            localAssignmentStore,
            contextForwarder,
            true
        )
        val ctx = DefaultCompletionContext(
            campaignKey = "my-campaign",
            scenarioName = "my-scenario",
            minionId = "my-minion",
            minionStart = 12756412L,
            lastExecutedStepName = "step-1",
            errors = emptyList()
        )
        every { localAssignmentStore.isLocal("my-scenario", "my-minion", "this-is-the-next-dag") } returns true

        // when
        step.complete(ctx)

        //then
        coVerifyOnce {
            factoryCampaignManager.notifyCompleteMinion(
                "my-minion",
                Instant.ofEpochMilli(12756412L),
                "my-campaign",
                "my-scenario",
                "this-is-my-dag"
            )
            localAssignmentStore.isLocal("my-scenario", "my-minion", "this-is-the-next-dag")
        }
        confirmVerified(factoryCampaignManager, contextForwarder)
    }

    @Test
    @Timeout(1)
    internal fun `should not complete pipeline operation when the flag is disabled`() = testCoroutineDispatcher.runTest {
        // given
        val step = DistributedDagTransitionStep<Int>(
            "",
            "this-is-my-dag",
            "this-is-the-next-dag",
            factoryCampaignManager,
            localAssignmentStore,
            contextForwarder,
            false
        )
        val ctx = DefaultCompletionContext(
            campaignKey = "my-campaign",
            scenarioName = "my-scenario",
            minionId = "my-minion",
            minionStart = 12756412L,
            lastExecutedStepName = "step-1",
            errors = emptyList()
        )
        every { localAssignmentStore.isLocal("my-scenario", "my-minion", "this-is-the-next-dag") } returns true

        // when
        step.complete(ctx)

        //then
        coVerifyOnce {
            localAssignmentStore.isLocal("my-scenario", "my-minion", "this-is-the-next-dag")
        }
        confirmVerified(factoryCampaignManager, contextForwarder)
    }

    @Test
    @Timeout(1)
    internal fun `should complete pipeline operation then forward the complete execution when the next DAG is remote`() =
        testCoroutineDispatcher.runTest {
            // given
            val step = DistributedDagTransitionStep<Int>(
                "",
                "this-is-my-dag",
                "this-is-the-next-dag",
                factoryCampaignManager,
                localAssignmentStore,
                contextForwarder,
                true
            )
            val ctx = DefaultCompletionContext(
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                minionId = "my-minion",
                minionStart = 12817612L,
                lastExecutedStepName = "step-1",
                errors = emptyList()
            )
            every { localAssignmentStore.isLocal("my-scenario", "my-minion", "this-is-the-next-dag") } returns false

            // when
            step.complete(ctx)

            // then
            coVerifyOnce {
                factoryCampaignManager.notifyCompleteMinion(
                    "my-minion",
                    Instant.ofEpochMilli(12817612L),
                    "my-campaign",
                    "my-scenario",
                    "this-is-my-dag"
                )
                localAssignmentStore.isLocal("my-scenario", "my-minion", "this-is-the-next-dag")
                contextForwarder.forward(refEq(ctx), listOf("this-is-the-next-dag"))
            }
            confirmVerified(factoryCampaignManager, contextForwarder)
        }
}