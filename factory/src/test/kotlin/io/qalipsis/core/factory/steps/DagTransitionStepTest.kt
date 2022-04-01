package io.qalipsis.core.factory.steps

import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.DefaultCompletionContext
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
    internal fun `should only forward input to output`() = testCoroutineDispatcher.runTest {
        // given
        val step = DagTransitionStep<Int>("", "this-is-my-dag", factoryCampaignManager)
        val ctx = StepTestHelper.createStepContext<Int, Int>(
            input = 1,
            campaignName = "my-campaign",
            scenarioName = "my-scenario",
            minionId = "my-minion"
        )

        // when
        step.execute(ctx)

        //then
        val output = ctx.consumeOutputValue()
        Assertions.assertEquals(1, output)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        confirmVerified(factoryCampaignManager)
    }


    @Test
    @Timeout(1)
    internal fun `should notify DAG completion when complete is called`() = testCoroutineDispatcher.runTest {
        // given
        val step = DagTransitionStep<Int>("", "this-is-my-dag", factoryCampaignManager)
        val ctx = DefaultCompletionContext(
            campaignName = "my-campaign",
            scenarioName = "my-scenario",
            minionId = "my-minion",
            lastExecutedStepName = "step-1",
            errors = emptyList()
        )

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
        }
        confirmVerified(factoryCampaignManager)
    }
}