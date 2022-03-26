package io.qalipsis.core.factory.steps

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isDataClassEqualTo
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.DefaultCompletionContext
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.StepError
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.factory.orchestration.TransportableCompletionContext
import io.qalipsis.core.factory.orchestration.TransportableContext
import io.qalipsis.core.factory.orchestration.TransportableStepContext
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.core.serialization.DistributionSerializer
import io.qalipsis.core.serialization.SerializationContext
import io.qalipsis.core.serialization.SerializedRecord
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration


@WithMockk
@ExperimentalLettuceCoroutinesApi
internal class BufferedContextForwarderTest : AbstractRedisIntegrationTest() {

    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var factoryChannel: FactoryChannel

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @RelaxedMockK
    private lateinit var minionAssignmentKeeper: MinionAssignmentKeeper

    @RelaxedMockK
    private lateinit var distributionSerializer: DistributionSerializer

    @Test
    @Timeout(10)
    internal fun `should forward contexts asynchronously to the relevant factories`() = testDispatcherProvider.run {
        // given
        val stepExecutionContext = StepTestHelper.createStepContext<Int, Int>(
            input = 1,
            campaignId = "my-campaign",
            scenarioId = "my-scenario-1",
            minionId = "my-minion-1"
        )
        val stepExecutionContextWithError = StepTestHelper.createStepContext<Int, Int>(
            input = 2,
            campaignId = "my-campaign",
            scenarioId = "my-scenario-1",
            minionId = "my-minion-2",
            isExhausted = true,
            errors = mutableListOf(StepError("This is an error"))
        )
        val completionContext = DefaultCompletionContext(
            campaignId = "my-campaign",
            scenarioId = "my-scenario-2",
            minionId = "my-minion-3",
            lastExecutedStepId = "step-1",
            errors = emptyList()
        )
        val completionContextWithError = DefaultCompletionContext(
            campaignId = "my-campaign",
            scenarioId = "my-scenario-1",
            minionId = "my-minion-4",
            lastExecutedStepId = "step-3",
            errors = listOf(StepError("This is an error"))
        )
        every { factoryCampaignManager.runningCampaign.campaignId } returns "my-campaign"
        every { factoryCampaignManager.runningCampaign.broadcastChannel } returns "the-broadcast-channel"

        coEvery {
            minionAssignmentKeeper.getFactoriesChannels(
                "my-campaign",
                "my-scenario-1",
                setOf("my-minion-1", "my-minion-2", "my-minion-4"),
                setOf("dag-1", "dag-2", "dag-4")
            )
        } returns com.google.common.collect.HashBasedTable.create<MinionId, DirectedAcyclicGraphId, String>().also {
            it.put("my-minion-1", "dag-1", "factory-1")
            it.put("my-minion-2", "dag-2", "factory-2")
        }
        coEvery {
            minionAssignmentKeeper.getFactoriesChannels(
                "my-campaign",
                "my-scenario-2",
                setOf("my-minion-3"),
                setOf("dag-3")
            )
        } returns com.google.common.collect.HashBasedTable.create<MinionId, DirectedAcyclicGraphId, String>().also {
            it.put("my-minion-3", "dag-3", "factory-2")
        }
        val countLatch = SuspendedCountLatch(4)
        val publishedDirectivesToFactory1 = mutableListOf<TransportableContext>()
        coEvery {
            factoryChannel.publishDirective("factory-1", capture(publishedDirectivesToFactory1))
        } coAnswers {
            countLatch.decrement()
        }
        val publishedDirectivesToFactory2 = mutableListOf<TransportableContext>()
        coEvery {
            factoryChannel.publishDirective("factory-2", capture(publishedDirectivesToFactory2))
        } coAnswers {
            countLatch.decrement()
        }
        val publishedDirectivesToBroadcast = mutableListOf<TransportableContext>()
        coEvery {
            factoryChannel.publishDirective("the-broadcast-channel", capture(publishedDirectivesToBroadcast))
        } coAnswers {
            countLatch.decrement()
        }
        val serializedRecords = listOf<SerializedRecord>(relaxedMockk(), relaxedMockk(), relaxedMockk(), relaxedMockk())
        every {
            distributionSerializer.serializeAsRecord(any<Int>(), SerializationContext.CONTEXT)
        } returnsMany serializedRecords

        val bufferedContextForwarder = BufferedContextForwarder(
            factoryChannel, factoryCampaignManager, minionAssignmentKeeper, distributionSerializer, this,
            10, Duration.ofSeconds(1)
        )

        // when
        bufferedContextForwarder.forward(stepExecutionContext, listOf("dag-1"))
        bufferedContextForwarder.forward(stepExecutionContextWithError, listOf("dag-2"))
        bufferedContextForwarder.forward(completionContext, listOf("dag-3"))
        bufferedContextForwarder.forward(completionContextWithError, listOf("dag-4"))
        countLatch.await()

        // then

        assertThat(publishedDirectivesToFactory1).all {
            hasSize(1)
            index(0).isDataClassEqualTo(TransportableStepContext(stepExecutionContext, serializedRecords[0]))
        }
        assertThat(publishedDirectivesToFactory2).all {
            hasSize(2)
            index(0).isDataClassEqualTo(TransportableStepContext(stepExecutionContextWithError, serializedRecords[1]))
            index(1).isDataClassEqualTo(TransportableCompletionContext(completionContext))
        }
        assertThat(publishedDirectivesToBroadcast).all {
            hasSize(1)
            index(0).isDataClassEqualTo(TransportableCompletionContext(completionContextWithError))
        }

        coVerifyOnce {
            distributionSerializer.serializeAsRecord(1, SerializationContext.CONTEXT)
            distributionSerializer.serializeAsRecord(2, SerializationContext.CONTEXT)

            minionAssignmentKeeper.getFactoriesChannels(
                "my-campaign",
                "my-scenario-1",
                setOf("my-minion-1", "my-minion-2", "my-minion-4"),
                setOf("dag-1", "dag-2", "dag-4")
            )
            minionAssignmentKeeper.getFactoriesChannels(
                "my-campaign",
                "my-scenario-2",
                setOf("my-minion-3"),
                setOf("dag-3")
            )
        }
    }
}