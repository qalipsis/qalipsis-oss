package io.qalipsis.core.factory.steps

import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
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
internal class DagTransitionStepTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @Test
    @Timeout(1)
    internal fun `should only forward input to output when the context is not a tail`() =
        testCoroutineDispatcher.runTest {
            // given
            val step = DagTransitionStep<Int>("", "this-is-my-dag", factoryCampaignManager)
            val ctx = StepTestHelper.createStepContext<Int, Int>(
                input = 1,
                campaignId = "my-campaign",
                scenarioId = "my-scenario",
                minionId = "my-minion",
                isTail = false
            )

            // when
            step.execute(ctx)

            //then
            val output = (ctx.output as Channel).receive()
            Assertions.assertEquals(1, output)
            Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
            confirmVerified(factoryCampaignManager)
        }


    @Test
    @Timeout(1)
    internal fun `should notify DAG completion and forward input to output when the context is a tail`() =
        testCoroutineDispatcher.runTest {
            // given
            val step = DagTransitionStep<Int>("", "this-is-my-dag", factoryCampaignManager)
            val ctx = StepTestHelper.createStepContext<Int, Int>(
                input = 1,
                campaignId = "my-campaign",
                scenarioId = "my-scenario",
                minionId = "my-minion",
                isTail = true
            )

            // when
            step.execute(ctx)

            //then
            val output = (ctx.output as Channel).receive()
            Assertions.assertEquals(1, output)
            Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
            coVerifyOnce {
                factoryCampaignManager.notifyCompleteMinion(
                    "my-minion",
                    "my-campaign",
                    "my-scenario",
                    "this-is-my-dag"
                )
            }
            confirmVerified(factoryCampaignManager)
        }


    @Test
    @Timeout(1)
    internal fun `should notify DAG completion and do nothing when the context is an exhausted tail`() =
        testCoroutineDispatcher.runTest {
            // given
            val step = DagTransitionStep<Int>("", "this-is-my-dag", factoryCampaignManager)
            val ctx = StepTestHelper.createStepContext<Int, Int>(
                input = 1,
                campaignId = "my-campaign",
                scenarioId = "my-scenario",
                minionId = "my-minion",
                isExhausted = true,
                isTail = true
            )

            // when
            step.execute(ctx)

            //then

            // The input was not received.
            Assertions.assertFalse((ctx.input as Channel).isEmpty)
            Assertions.assertTrue((ctx.output as Channel).isEmpty)
            Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
            coVerifyOnce {
                factoryCampaignManager.notifyCompleteMinion(
                    "my-minion",
                    "my-campaign",
                    "my-scenario",
                    "this-is-my-dag"
                )
            }
            confirmVerified(factoryCampaignManager)
        }

    @Test
    @Timeout(1)
    internal fun `should do nothing when the context is exhausted and not a tail`() =
        testCoroutineDispatcher.runTest {
            // given
            val step = DagTransitionStep<Int>("", "this-is-my-dag", factoryCampaignManager)
            val ctx = StepTestHelper.createStepContext<Int, Int>(
                input = 1,
                campaignId = "my-campaign",
                scenarioId = "my-scenario",
                minionId = "my-minion",
                isExhausted = true,
                isTail = false
            )

            // when
            step.execute(ctx)

            //then
            // The input was not received.
            Assertions.assertFalse((ctx.input as Channel).isEmpty)
            Assertions.assertTrue((ctx.output as Channel).isEmpty)
            Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
            confirmVerified(factoryCampaignManager)
        }
}