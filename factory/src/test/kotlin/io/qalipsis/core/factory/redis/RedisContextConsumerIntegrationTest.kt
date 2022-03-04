package io.qalipsis.core.factory.redis

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.lang.concurrentList
import io.qalipsis.api.runtime.DirectedAcyclicGraph
import io.qalipsis.api.runtime.Minion
import io.qalipsis.api.steps.Step
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.factory.orchestration.Runner
import io.qalipsis.core.factory.orchestration.ScenarioRegistry
import io.qalipsis.core.factory.orchestration.StepError
import io.qalipsis.core.factory.orchestration.TransportableCompletionContext
import io.qalipsis.core.factory.orchestration.TransportableStepContext
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.core.serialization.DistributionSerializer
import io.qalipsis.core.serialization.SerializationContext
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

@PropertySource(
    Property(name = ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY, value = ExecutionEnvironments.REDIS)
)
@WithMockk
@ExperimentalLettuceCoroutinesApi
internal class RedisContextConsumerIntegrationTest : AbstractRedisIntegrationTest() {

    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Inject
    private lateinit var distributionSerializer: DistributionSerializer

    @Inject
    private lateinit var idGenerator: IdGenerator

    @Inject
    private lateinit var redisCommands: RedisCoroutinesCommands<String, String>

    @RelaxedMockK
    private lateinit var factoryConfiguration: FactoryConfiguration

    @RelaxedMockK
    private lateinit var scenarioRegistry: ScenarioRegistry

    @RelaxedMockK
    private lateinit var minionsKeeper: MinionsKeeper

    @RelaxedMockK
    private lateinit var localAssignmentStore: LocalAssignmentStore

    @RelaxedMockK
    private lateinit var runner: Runner

    @Test
    @Timeout(10)
    internal fun `should execute received step contexts`() = testDispatcherProvider.run {
        // given
        val stepExecutionContext1 = TransportableStepContext(
            input = null,
            campaignId = "my-campaign",
            scenarioId = "my-scenario-1",
            minionId = "my-minion-1",
            previousStepId = "my-previous-step",
            stepId = "my-step",
            stepType = "the-step-type",
            stepFamily = "the-step-family",
            stepIterationIndex = 123,
            isExhausted = false,
            isTail = false,
            errors = emptyList()
        )
        val stepExecutionContext2 = TransportableStepContext(
            input = distributionSerializer.serializeAsRecord("Transported value"),
            campaignId = "my-campaign",
            scenarioId = "my-scenario-2",
            minionId = "my-minion-2",
            previousStepId = "my-other-previous-step",
            stepId = "my-other-step",
            stepType = "the-other-step-type",
            stepFamily = "the-other-step-family",
            stepIterationIndex = 12,
            isExhausted = true,
            isTail = true,
            errors = listOf(
                StepError("error-1", "the step of error 1"),
                StepError("error-2", "the step of error 2")
            )
        )
        val redisContextConsumer = RedisContextConsumer(
            distributionSerializer,
            factoryConfiguration,
            idGenerator,
            redisCommands,
            scenarioRegistry,
            minionsKeeper,
            localAssignmentStore,
            runner,
            this
        )
        every { factoryConfiguration.unicastContextsChannel } returns "unicast-channel"
        every { factoryConfiguration.broadcastContextsChannel } returns "broadcast-channel"

        // when
        redisCommands.xadd(
            "unicast-channel", mapOf(
                "1" to distributionSerializer.serialize(stepExecutionContext1, SerializationContext.CONTEXT)
                    .decodeToString()
            )
        )
        redisCommands.xadd(
            "broadcast-channel", mapOf(
                "131" to distributionSerializer.serialize(stepExecutionContext2, SerializationContext.CONTEXT)
                    .decodeToString()
            )
        )
        val minion1 = relaxedMockk<Minion>("minion-1")
        val minion2 = relaxedMockk<Minion>("minion-2")
        every { minionsKeeper["my-minion-1"] } returns minion1
        every { minionsKeeper["my-minion-2"] } returns minion2
        val step1 = relaxedMockk<Step<*, *>>("step-1")
        val step2 = relaxedMockk<Step<*, *>>("step-2")
        coEvery { scenarioRegistry["my-scenario-1"]!!.findStep("my-step") } returns (step1 to relaxedMockk())
        coEvery { scenarioRegistry["my-scenario-2"]!!.findStep("my-other-step") } returns (step2 to relaxedMockk())

        val countLatch = SuspendedCountLatch(2)
        val contexts = concurrentList<StepContext<*, *>>()
        coEvery { runner.runMinion(any(), any(), capture(contexts), any()) } coAnswers { countLatch.decrement() }
        redisContextConsumer.start()

        // then
        countLatch.await()
        coVerifyOnce {
            runner.runMinion(refEq(minion1), refEq(step1), any())
            runner.runMinion(refEq(minion2), refEq(step2), any())
        }
        assertThat(contexts.sortedBy { it.minionId }).all {
            hasSize(2)
            index(0).all {
                prop(StepContext<*, *>::hasInput).isFalse()
                prop(StepContext<*, *>::campaignId).isEqualTo("my-campaign")
                prop(StepContext<*, *>::scenarioId).isEqualTo("my-scenario-1")
                prop(StepContext<*, *>::minionId).isEqualTo("my-minion-1")
                prop(StepContext<*, *>::previousStepId).isEqualTo("my-previous-step")
                prop(StepContext<*, *>::stepId).isEqualTo("my-step")
                prop(StepContext<*, *>::stepType).isEqualTo("the-step-type")
                prop(StepContext<*, *>::stepFamily).isEqualTo("the-step-family")
                prop(StepContext<*, *>::stepIterationIndex).isEqualTo(123)
                prop(StepContext<*, *>::isExhausted).isFalse()
                prop(StepContext<*, *>::isTail).isFalse()
                prop(StepContext<*, *>::errors).isEmpty()
            }
            index(1).all {
                prop(StepContext<*, *>::hasInput).isTrue()
                transform("input") { runBlocking { it.receive() } }.isEqualTo("Transported value")
                prop(StepContext<*, *>::campaignId).isEqualTo("my-campaign")
                prop(StepContext<*, *>::scenarioId).isEqualTo("my-scenario-2")
                prop(StepContext<*, *>::minionId).isEqualTo("my-minion-2")
                prop(StepContext<*, *>::previousStepId).isEqualTo("my-other-previous-step")
                prop(StepContext<*, *>::stepId).isEqualTo("my-other-step")
                prop(StepContext<*, *>::stepType).isEqualTo("the-other-step-type")
                prop(StepContext<*, *>::stepFamily).isEqualTo("the-other-step-family")
                prop(StepContext<*, *>::stepIterationIndex).isEqualTo(12)
                prop(StepContext<*, *>::isExhausted).isTrue()
                prop(StepContext<*, *>::isTail).isTrue()
                prop(StepContext<*, *>::errors).all {
                    hasSize(2)
                    index(0).isEqualTo(io.qalipsis.api.context.StepError("error-1", "the step of error 1"))
                    index(1).isEqualTo(io.qalipsis.api.context.StepError("error-2", "the step of error 2"))
                }
            }
        }
        confirmVerified(runner)
        redisContextConsumer.stop()
    }

    @Test
    @Timeout(10)
    internal fun `should execute received completion contexts`() = testDispatcherProvider.run {
        // given
        val completionContext1 = TransportableCompletionContext(
            campaignId = "my-campaign",
            scenarioId = "my-scenario-1",
            minionId = "my-minion-1",
            lastExecutedStepId = "my-step",
            errors = emptyList()
        )
        val completionContext2 = TransportableCompletionContext(
            campaignId = "my-campaign",
            scenarioId = "my-scenario-2",
            minionId = "my-minion-2",
            lastExecutedStepId = "my-other-step",
            errors = listOf(
                StepError("error-1", "the step of error 1"),
                StepError("error-2", "the step of error 2")
            )
        )
        val redisContextConsumer = RedisContextConsumer(
            distributionSerializer,
            factoryConfiguration,
            idGenerator,
            redisCommands,
            scenarioRegistry,
            minionsKeeper,
            localAssignmentStore,
            runner,
            this
        )
        every { factoryConfiguration.unicastContextsChannel } returns "unicast-channel"
        every { factoryConfiguration.broadcastContextsChannel } returns "broadcast-channel"

        // when
        redisCommands.xadd(
            "unicast-channel", mapOf(
                "1" to distributionSerializer.serialize(completionContext1, SerializationContext.CONTEXT)
                    .decodeToString()
            )
        )
        redisCommands.xadd(
            "broadcast-channel", mapOf(
                "131" to distributionSerializer.serialize(completionContext2, SerializationContext.CONTEXT)
                    .decodeToString()
            )
        )
        val minion1 = relaxedMockk<Minion>("minion-1")
        val minion2 = relaxedMockk<Minion>("minion-2")
        every { minionsKeeper["my-minion-1"] } returns minion1
        every { minionsKeeper["my-minion-2"] } returns minion2
        val step1 = relaxedMockk<Step<*, *>>("step-1")
        val locallyExecuted1 = relaxedMockk<DirectedAcyclicGraph>("locallyExecuted-1") {
            every { id } returns "dag-1"
            coEvery { rootStep.forceGet() } returns step1
        }
        val step2 = relaxedMockk<Step<*, *>>("step-2")
        val notLocallyExecuted1 = relaxedMockk<DirectedAcyclicGraph>("notLocallyExecuted-1") {
            every { id } returns "dag-2"
            coEvery { rootStep.forceGet() } returns step2
        }
        val step3 = relaxedMockk<Step<*, *>>("step-3")
        val locallyExecuted3 = relaxedMockk<DirectedAcyclicGraph>("locallyExecuted-2") {
            every { id } returns "dag-3"
            coEvery { rootStep.forceGet() } returns step3
        }
        val step4 = relaxedMockk<Step<*, *>>("step-4")
        val notLocallyExecuted2 = relaxedMockk<DirectedAcyclicGraph>("notLocallyExecuted-1") {
            every { id } returns "dag-1"
            coEvery { rootStep.forceGet() } returns step4
        }
        val step5 = relaxedMockk<Step<*, *>>("step-5")
        val locallyExecuted4 = relaxedMockk<DirectedAcyclicGraph>("locallyExecuted-2") {
            every { id } returns "dag-2"
            coEvery { rootStep.forceGet() } returns step5
        }
        coEvery { scenarioRegistry["my-scenario-1"]!!.dags } returns listOf(
            locallyExecuted1,
            notLocallyExecuted1,
            locallyExecuted3
        )
        coEvery { scenarioRegistry["my-scenario-2"]!!.dags } returns listOf(notLocallyExecuted2, locallyExecuted4)
        coEvery {
            localAssignmentStore.isLocal(
                "my-scenario-1",
                "my-minion-1",
                "dag-1"
            )
        } returns true // locallyExecuted1
        coEvery {
            localAssignmentStore.isLocal(
                "my-scenario-1",
                "my-minion-1",
                "dag-2"
            )
        } returns false // notLocallyExecuted1
        coEvery {
            localAssignmentStore.isLocal(
                "my-scenario-1",
                "my-minion-1",
                "dag-3"
            )
        } returns true // locallyExecuted3
        coEvery {
            localAssignmentStore.isLocal(
                "my-scenario-2",
                "my-minion-2",
                "dag-1"
            )
        } returns false // notLocallyExecuted2
        coEvery {
            localAssignmentStore.isLocal(
                "my-scenario-2",
                "my-minion-2",
                "dag-2"
            )
        } returns true // locallyExecuted4

        val countLatch = SuspendedCountLatch(3)
        val contexts = concurrentList<CompletionContext>()
        coEvery { runner.complete(any(), any(), capture(contexts)) } coAnswers {
            countLatch.decrement()
            relaxedMockk()
        }
        redisContextConsumer.start()

        // then
        countLatch.await()
        coVerifyOnce {
            runner.complete(refEq(minion1), refEq(step1), any())
            runner.complete(refEq(minion1), refEq(step3), any())
            runner.complete(refEq(minion2), refEq(step5), any())
        }
        assertThat(contexts.sortedBy { it.minionId }).all {
            hasSize(3)
            index(0).all {
                prop(CompletionContext::campaignId).isEqualTo("my-campaign")
                prop(CompletionContext::scenarioId).isEqualTo("my-scenario-1")
                prop(CompletionContext::minionId).isEqualTo("my-minion-1")
                prop(CompletionContext::lastExecutedStepId).isEqualTo("my-step")
            }
            index(1).isSameAs(contexts.first())
            index(2).all {
                prop(CompletionContext::campaignId).isEqualTo("my-campaign")
                prop(CompletionContext::scenarioId).isEqualTo("my-scenario-2")
                prop(CompletionContext::minionId).isEqualTo("my-minion-2")
                prop(CompletionContext::lastExecutedStepId).isEqualTo("my-other-step")
            }
        }
        confirmVerified(runner)
        redisContextConsumer.stop()
    }
}