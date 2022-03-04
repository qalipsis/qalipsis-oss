package io.qalipsis.core.factory.redis

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isDataClassEqualTo
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.test.annotation.MockBean
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.DefaultCompletionContext
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.StepError
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.factory.orchestration.TransportableCompletionContext
import io.qalipsis.core.factory.orchestration.TransportableContext
import io.qalipsis.core.factory.orchestration.TransportableStepContext
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.core.redis.RedisConsumerClient
import io.qalipsis.core.serialization.DistributionSerializer
import io.qalipsis.core.serialization.SerializationContext
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.lang.TestIdGenerator
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.steps.StepTestHelper
import jakarta.inject.Inject
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

@PropertySource(
    Property(name = ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY, value = ExecutionEnvironments.REDIS)
)
@WithMockk
@ExperimentalLettuceCoroutinesApi
internal class RedisContextForwarderIntegrationTest : AbstractRedisIntegrationTest() {

    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Inject
    private lateinit var redisContextForwarder: RedisContextForwarder

    @Inject
    private lateinit var redisCommands: RedisCoroutinesCommands<String, String>

    @Inject
    private lateinit var distributionSerializer: DistributionSerializer

    @Inject
    private lateinit var factoryConfiguration: FactoryConfiguration

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @RelaxedMockK
    private lateinit var minionAssignmentKeeper: MinionAssignmentKeeper

    @MockBean(MinionAssignmentKeeper::class)
    fun minionAssignmentKeeper(): MinionAssignmentKeeper = minionAssignmentKeeper

    @MockBean(FactoryCampaignManager::class)
    fun factoryCampaignManager(): FactoryCampaignManager = factoryCampaignManager

    @Test
    @Timeout(10)
    internal fun `should forward contexts asynchronously to the relevant factories`() = testDispatcherProvider.run {
        // given
        val stepExecutionContext1 = StepTestHelper.createStepContext<Int, Int>(
            input = 1,
            campaignId = "my-campaign",
            scenarioId = "my-scenario-1",
            minionId = "my-minion-1"
        )
        val stepExecutionContext2 = StepTestHelper.createStepContext<String, Int>(
            input = "The value",
            campaignId = "my-campaign",
            scenarioId = "my-scenario-1",
            minionId = "my-minion-2",
            isExhausted = true,
            errors = mutableListOf(StepError("This is an error"))
        )
        val completionContext1 = DefaultCompletionContext(
            campaignId = "my-campaign",
            scenarioId = "my-scenario-2",
            minionId = "my-minion-3",
            lastExecutedStepId = "step-1",
            errors = emptyList()
        )
        val completionContext2 = DefaultCompletionContext(
            campaignId = "my-campaign",
            scenarioId = "my-scenario-1",
            minionId = "my-minion-4",
            lastExecutedStepId = "step-3",
            errors = listOf(StepError("This is an error"))
        )

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
        every { factoryCampaignManager.runningCampaign } returns "my-campaign"
        factoryConfiguration.broadcastContextsChannel = "all-contexts"

        val countLatch = SuspendedCountLatch(4)
        val redisConsumerClients = mutableListOf<RedisConsumerClient<*>>()
        val resultsFactory1 = mutableListOf<TransportableContext>()
        redisConsumerClients += RedisConsumerClient<TransportableContext>(
            redisCommands,
            { distributionSerializer.deserialize(it.encodeToByteArray())!! },
            idGenerator = TestIdGenerator,
            "consumer-factory-1",
            "factory-1"
        ) {
            resultsFactory1.add(it)
            countLatch.decrement()
        }

        val resultsFactory2 = mutableListOf<TransportableContext>()
        redisConsumerClients += RedisConsumerClient<TransportableContext>(
            redisCommands,
            { distributionSerializer.deserialize(it.encodeToByteArray())!! },
            idGenerator = TestIdGenerator,
            "consumer-factory-2",
            "factory-2"
        ) {
            resultsFactory2.add(it)
            countLatch.decrement()
        }

        val resultsBroadcast = mutableListOf<TransportableContext>()
        redisConsumerClients += RedisConsumerClient<TransportableContext>(
            redisCommands,
            { distributionSerializer.deserialize(it.encodeToByteArray())!! },
            idGenerator = TestIdGenerator,
            "consumer-broadcast",
            "all-contexts"
        ) {
            resultsBroadcast.add(it)
            countLatch.decrement()
        }

        redisConsumerClients.forEach {
            this.launch { it.start() }
        }

        // when
        redisContextForwarder.forward(stepExecutionContext1, listOf("dag-1"))
        redisContextForwarder.forward(stepExecutionContext2, listOf("dag-2"))
        redisContextForwarder.forward(completionContext1, listOf("dag-3"))
        redisContextForwarder.forward(completionContext2, listOf("dag-4"))
        countLatch.await()

        // then
        redisConsumerClients.forEach {
            this.launch { it.stop() }
        }

        assertThat(resultsFactory1).all {
            hasSize(1)
            index(0).isDataClassEqualTo(
                TransportableStepContext(
                    stepExecutionContext1,
                    distributionSerializer.serializeAsRecord(1, SerializationContext.CONTEXT)
                )
            )
        }
        assertThat(resultsFactory2).all {
            hasSize(2)
            any {
                it.isDataClassEqualTo(
                    TransportableStepContext(
                        stepExecutionContext2,
                        distributionSerializer.serializeAsRecord("The value", SerializationContext.CONTEXT)
                    )
                )
            }
            any { it.isDataClassEqualTo(TransportableCompletionContext(completionContext1)) }
        }
        assertThat(resultsBroadcast).all {
            hasSize(1)
            index(0).isDataClassEqualTo(TransportableCompletionContext(completionContext2))
        }

        coVerifyOnce {
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