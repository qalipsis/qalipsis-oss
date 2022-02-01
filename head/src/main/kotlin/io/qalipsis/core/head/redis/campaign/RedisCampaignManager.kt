@file:OptIn(ExperimentalLettuceCoroutinesApi::class)

package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.DirectiveProducer
import io.qalipsis.core.feedbacks.FeedbackHeadChannel
import io.qalipsis.core.head.campaign.AbstractCampaignManager
import io.qalipsis.core.head.campaign.CampaignConfiguration
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.head.orchestration.FactoryDirectedAcyclicGraphAssignmentResolver
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Singleton
@Requires(notEnv = [ExecutionEnvironments.STANDALONE])
internal class RedisCampaignManager(
    feedbackHeadChannel: FeedbackHeadChannel,
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) coroutineScope: CoroutineScope,
    directiveProducer: DirectiveProducer,
    factoryService: FactoryService,
    assignmentResolver: FactoryDirectedAcyclicGraphAssignmentResolver,
    campaignService: CampaignService,
    idGenerator: IdGenerator,
    campaignReportStateKeeper: CampaignReportStateKeeper,
    private val redisOperations: CampaignRedisOperations
) : AbstractCampaignManager(
    feedbackHeadChannel,
    coroutineScope,
    directiveProducer,
    factoryService,
    assignmentResolver,
    campaignService,
    idGenerator,
    campaignReportStateKeeper
) {

    override suspend fun create(campaign: CampaignConfiguration): CampaignExecutionState =
        RedisFactoryAssignmentState(campaign, redisOperations)

    override suspend fun get(campaignId: CampaignId): CampaignExecutionState {
        val currentState = redisOperations.getState(campaignId)
        val executionState = when (currentState?.second) {
            CampaignRedisState.FACTORY_DAGS_ASSIGNMENT_STATE -> RedisFactoryAssignmentState(
                currentState.first,
                redisOperations
            )
            CampaignRedisState.MINIONS_ASSIGNMENT_STATE -> RedisMinionsAssignmentState(
                currentState.first,
                redisOperations
            )
            CampaignRedisState.WARMUP_STATE -> RedisWarmupState(currentState.first, redisOperations)
            CampaignRedisState.MINIONS_STARTUP_STATE -> RedisMinionsStartupState(currentState.first, redisOperations)
            CampaignRedisState.RUNNING_STATE -> RedisRunningState(currentState.first, redisOperations)
            CampaignRedisState.COMPLETION_STATE -> RedisCompletionState(currentState.first, redisOperations)
            CampaignRedisState.FAILURE_STATE -> RedisFailureState(currentState.first, redisOperations)
            null -> throw IllegalStateException("The state of the campaign execution is unknown")
        }
        // Since the state is rebuilt, it is marked as already initialized.
        executionState.initialized = true

        return executionState
    }

    /**
     * Nothing to do, since the
     */
    override suspend fun set(state: CampaignExecutionState) = Unit

}