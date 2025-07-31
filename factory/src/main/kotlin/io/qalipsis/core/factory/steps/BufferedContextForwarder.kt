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

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepContext
import io.qalipsis.core.collections.LingerCollection
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.factory.orchestration.TransportableCompletionContext
import io.qalipsis.core.factory.orchestration.TransportableContext
import io.qalipsis.core.factory.orchestration.TransportableStepContext
import io.qalipsis.core.serialization.DistributionSerializer
import io.qalipsis.core.serialization.SerializationContext
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import javax.validation.constraints.Positive

/**
 * Implementation of [ContextForwarder] using Redis Streams to transport the [TransportableContext]s between
 * factories.
 *
 * @author Eric Jessé
 */
@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.FACTORY]),
    Requires(notEnv = [ExecutionEnvironments.SINGLE_FACTORY])
)
@ExperimentalLettuceCoroutinesApi
class BufferedContextForwarder(
    private val factoryChannel: FactoryChannel,
    private val factoryCampaignManager: FactoryCampaignManager,
    private val minionAssignmentKeeper: MinionAssignmentKeeper,
    private val distributionSerializer: DistributionSerializer,
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val backgroundScope: CoroutineScope,
    @Positive @Property(name = "transport.contexts.batch-size", defaultValue = "500")
    private val batchSize: Int,
    @PositiveDuration @Property(name = "transport.contexts.linger-duration", defaultValue = "2s")
    private val bufferDuration: Duration
) : ContextForwarder {

    /**
     * Buffers that releases [batchSize] elements when either [batchSize] is reached or [bufferDuration] time out was reached.
     */
    private val contextsBuffer = LingerCollection<Pair<TransportableContext, Collection<DirectedAcyclicGraphName>>>(
        batchSize, bufferDuration
    ) { contexts ->
        backgroundScope.launch { forward(contexts) }
    }

    override suspend fun forward(context: StepContext<*, *>, dags: Collection<DirectedAcyclicGraphName>) {
        val record = if (context.hasInput) {
            val input = context.receive()
            distributionSerializer.serializeAsRecord(input, SerializationContext.CONTEXT)
        } else {
            null
        }
        contextsBuffer.add(TransportableStepContext(context, record) to dags)
    }

    override suspend fun forward(context: CompletionContext, dags: Collection<DirectedAcyclicGraphName>) {
        contextsBuffer.add(TransportableCompletionContext(context) to dags)
    }

    /**
     * Forwards the relevant context to the next step.
     */
    private suspend fun forward(contexts: List<Pair<TransportableContext, Collection<DirectedAcyclicGraphName>>>) {
        // Lists the factories channels for all the minions and DAGs.
        val factoriesChannels = contexts.groupBy { it.first.scenarioName }.mapValues { (scenarioName, ctx) ->
            val minions = ctx.map { it.first.minionId }.toSet()
            val nextDagsIds = ctx.flatMap { it.second }.toSet()

            minionAssignmentKeeper.getFactoriesChannels(
                factoryCampaignManager.runningCampaign.campaignKey,
                scenarioName,
                minions,
                nextDagsIds
            )
        }

        // Groups the contexts by channel.
        contexts.forEach { (context, dags) ->
            dags.forEach { dag ->
                val channel = factoriesChannels[context.scenarioName]?.get(context.minionId, dag)
                    ?: factoryCampaignManager.runningCampaign.broadcastChannel
                factoryChannel.publishDirective(channel, context)
            }
        }
    }

}