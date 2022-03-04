package io.qalipsis.core.factory.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.factory.orchestration.Runner
import io.qalipsis.core.factory.orchestration.ScenarioRegistry
import io.qalipsis.core.factory.orchestration.TransportableCompletionContext
import io.qalipsis.core.factory.orchestration.TransportableContext
import io.qalipsis.core.factory.orchestration.TransportableStepContext
import io.qalipsis.core.factory.steps.ContextConsumer
import io.qalipsis.core.redis.RedisConsumerClient
import io.qalipsis.core.serialization.DeserializationContext
import io.qalipsis.core.serialization.DistributionSerializer
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Implementation of [ContextConsumer] using Redis Streams to receive the [TransportableContext]s from other
 * factories.
 *
 * @author Eric Jessé
 */
@Singleton
@Requirements(
    Requires(notEnv = [ExecutionEnvironments.STANDALONE, ExecutionEnvironments.SINGLE_FACTORY]),
    Requires(
        property = ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY,
        value = ExecutionEnvironments.REDIS
    )
)
@ExperimentalLettuceCoroutinesApi
internal class RedisContextConsumer(
    private val distributionSerializer: DistributionSerializer,
    private val factoryConfiguration: FactoryConfiguration,
    private val idGenerator: IdGenerator,
    private val redisCommands: RedisCoroutinesCommands<String, String>,
    private val scenarioRegistry: ScenarioRegistry,
    private val minionsKeeper: MinionsKeeper,
    private val localAssignmentStore: LocalAssignmentStore,
    private val runner: Runner,
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val backgroundScope: CoroutineScope,
) : ContextConsumer {

    private val consumers = mutableListOf<RedisConsumerClient<TransportableContext>>()

    private val consumersJobs = mutableListOf<Job>()

    override suspend fun start() {
        consumers += RedisConsumerClient(
            redisCommands,
            { distributionSerializer.deserialize(it.encodeToByteArray(), DeserializationContext.CONTEXT)!! },
            idGenerator = idGenerator,
            factoryConfiguration.nodeId,
            factoryConfiguration.unicastContextsChannel
        ) { context -> processContext(context) }

        consumers += RedisConsumerClient(
            redisCommands,
            { distributionSerializer.deserialize(it.encodeToByteArray(), DeserializationContext.CONTEXT)!! },
            idGenerator = idGenerator,
            factoryConfiguration.nodeId,
            factoryConfiguration.broadcastContextsChannel
        ) { context -> processContext(context) }

        consumers.forEach {
            consumersJobs += backgroundScope.launch {
                it.start()
            }
        }
    }

    private suspend fun processContext(transportableContext: TransportableContext) {
        if (transportableContext is TransportableStepContext) {
            executeStep(transportableContext)
        } else if (transportableContext is TransportableCompletionContext) {
            completeStep(transportableContext)
        }
    }

    private suspend fun executeStep(transportableContext: TransportableStepContext) {
        val minion = minionsKeeper[transportableContext.minionId]
        val step = scenarioRegistry[transportableContext.scenarioId]!!.findStep(transportableContext.stepId)!!.first
        val input = transportableContext.input?.let { distributionSerializer.deserializeRecord<Any?>(it) }
        val stepExecutionContext = transportableContext.toContext(input, transportableContext.input != null)
        runner.runMinion(minion, step, stepExecutionContext)
    }

    private suspend fun completeStep(transportableContext: TransportableCompletionContext) {
        val minion = minionsKeeper[transportableContext.minionId]
        val completionContext = transportableContext.toContext()

        scenarioRegistry[transportableContext.scenarioId]?.dags?.asSequence()?.filter {
            localAssignmentStore.isLocal(transportableContext.scenarioId, transportableContext.minionId, it.id)
        }?.forEach { dag ->
            runner.complete(minion, dag.rootStep.forceGet(), completionContext)
        }
    }

    override suspend fun stop() {
        consumers.forEach {
            kotlin.runCatching { it.stop() }
        }
        consumers.clear()

        consumersJobs.forEach {
            kotlin.runCatching { it.cancel() }
        }
        consumersJobs.clear()
    }
}