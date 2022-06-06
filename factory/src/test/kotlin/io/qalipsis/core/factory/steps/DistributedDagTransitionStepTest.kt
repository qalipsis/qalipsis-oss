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

@WithMockk
internal class DistributedDagTransitionStepTest {

    @JvmField
    @RegisterExtension
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
            contextForwarder
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
            contextForwarder
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
            contextForwarder
        )
        val ctx = DefaultCompletionContext(
            campaignKey = "my-campaign",
            scenarioName = "my-scenario",
            minionId = "my-minion",
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
    internal fun `should complete pipeline operation then forward the complete execution when the next DAG is remote`() =
        testCoroutineDispatcher.runTest {
            // given
            val step = DistributedDagTransitionStep<Int>(
                "",
                "this-is-my-dag",
                "this-is-the-next-dag",
                factoryCampaignManager,
                localAssignmentStore,
                contextForwarder
            )
            val ctx = DefaultCompletionContext(
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                minionId = "my-minion",
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