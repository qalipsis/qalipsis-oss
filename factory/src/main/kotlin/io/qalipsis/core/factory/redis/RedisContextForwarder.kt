package io.qalipsis.core.factory.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.collections.LingerCollection
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.factory.orchestration.TransportableCompletionContext
import io.qalipsis.core.factory.orchestration.TransportableContext
import io.qalipsis.core.factory.orchestration.TransportableStepContext
import io.qalipsis.core.factory.steps.ContextForwarder
import io.qalipsis.core.serialization.DistributionSerializer
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import javax.validation.constraints.Positive

@Singleton
@Requirements(
    Requires(notEnv = [ExecutionEnvironments.STANDALONE, ExecutionEnvironments.SINGLE_FACTORY]),
    Requires(
        property = ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY,
        value = ExecutionEnvironments.REDIS
    )
)
@ExperimentalLettuceCoroutinesApi
internal class RedisContextForwarder(
    private val serializer: DistributionSerializer,
    private val idGenerator: IdGenerator,
    private val redisCoroutinesCommands: RedisCoroutinesCommands<String, String>,
    private val factoryConfiguration: FactoryConfiguration,
    private val factoryCampaignManager: FactoryCampaignManager,
    private val minionAssignmentKeeper: MinionAssignmentKeeper,
    private val distributionSerializer: DistributionSerializer,
    @Named(Executors.CAMPAIGN_EXECUTOR_NAME) private val campaignScope: CoroutineScope,
    @Positive @Property(name = "transport.contexts.batch-size", defaultValue = "500")
    private val batchSize: Int,
    @PositiveDuration @Property(name = "transport.contexts.linger-duration", defaultValue = "2s")
    private val bufferDuration: Duration
) : ContextForwarder {

    // When 500 contexts are collected or 2 seconds passed, the buffered contexts are forwarded.
    private val contextsBuffer = LingerCollection<Pair<TransportableContext, Collection<DirectedAcyclicGraphId>>>(
        batchSize, bufferDuration
    ) { contexts ->
        campaignScope.launch { forward(contexts) }
    }

    override suspend fun forward(context: StepContext<*, *>, dags: Collection<DirectedAcyclicGraphId>) {
        val input = if (context.hasInput) {
            val input = context.receive()
            distributionSerializer.serialize(input)
        } else {
            null
        }
        contextsBuffer.add(TransportableStepContext(context, input) to dags)
    }

    override suspend fun forward(context: CompletionContext, dags: Collection<DirectedAcyclicGraphId>) {
        contextsBuffer.add(TransportableCompletionContext(context) to dags)
    }

    /**
     * Forwards the relevant context to the next step.
     */
    private suspend fun forward(contexts: List<Pair<TransportableContext, Collection<DirectedAcyclicGraphId>>>) {
        // Lists the factories channels for all the minions and DAGs.
        val factoriesChannels = contexts.groupBy { it.first.scenarioId }.mapValues { (scenarioId, ctx) ->
            val minions = ctx.map { it.first.minionId }.toSet()
            val nextDagsIds = ctx.flatMap { it.second }.toSet()

            minionAssignmentKeeper.getFactoriesChannels(
                factoryCampaignManager.runningCampaign,
                scenarioId,
                minions,
                nextDagsIds
            )
        }

        // Groups the contexts by channel.
        val batches = mutableMapOf<String, MutableList<TransportableContext>>()
        contexts.forEach { (context, dags) ->
            dags.forEach { dag ->
                val factoryChannel = factoriesChannels[context.scenarioId]?.get(context.minionId, dag)
                    ?: factoryConfiguration.broadcastContextsChannel
                batches.computeIfAbsent(factoryChannel) { mutableListOf() }.add(context)
            }
        }

        // Forwards the batches of channels.
        batches.forEach { (channel, contexts) ->
            forward(channel, contexts)
        }
    }

    @LogInput
    override suspend fun forward(factoryChannel: String, contexts: Collection<TransportableContext>) {
        redisCoroutinesCommands.xadd(factoryChannel, contexts.associate { context ->
            idGenerator.short() to serializer.serialize(context).decodeToString()
        })
    }
}