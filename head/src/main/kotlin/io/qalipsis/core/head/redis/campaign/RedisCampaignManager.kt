@file:OptIn(ExperimentalLettuceCoroutinesApi::class)

package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.context.CampaignName
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.communication.HeadChannel
import io.qalipsis.core.head.campaign.AbstractCampaignManager
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.head.orchestration.FactoryDirectedAcyclicGraphAssignmentResolver
import jakarta.inject.Singleton

/**
 * Component to manage a new Campaign for all the known scenarios when the campaign management is shared across
 * several heads.
 *
 * @author Eric Jessé
 */
@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD]),
    Requires(notEnv = [ExecutionEnvironments.SINGLE_HEAD])
)
internal class RedisCampaignManager(
    headChannel: HeadChannel,
    factoryService: FactoryService,
    assignmentResolver: FactoryDirectedAcyclicGraphAssignmentResolver,
    campaignService: CampaignService,
    campaignReportStateKeeper: CampaignReportStateKeeper,
    headConfiguration: HeadConfiguration,
    private val campaignExecutionContext: CampaignExecutionContext,
    private val redisOperations: CampaignRedisOperations,
) : AbstractCampaignManager<CampaignExecutionContext>(
    headChannel,
    factoryService,
    assignmentResolver,
    campaignService,
    campaignReportStateKeeper,
    headConfiguration,
    campaignExecutionContext
) {

    override suspend fun create(campaign: CampaignConfiguration): CampaignExecutionState<CampaignExecutionContext> =
        RedisFactoryAssignmentState(campaign, redisOperations)

    override suspend fun get(campaignName: CampaignName): CampaignExecutionState<CampaignExecutionContext> {
        val currentState = redisOperations.getState(campaignName)
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
            else -> throw IllegalStateException("The state of the campaign execution is unknown")
        }
        executionState.inject(campaignExecutionContext)

        // Since the state is rebuilt, it is marked as already initialized.
        executionState.initialized = true

        return executionState
    }

    /**
     * Nothing to do, since the
     */
    override suspend fun set(state: CampaignExecutionState<CampaignExecutionContext>) = Unit

    private companion object {

        val log = logger()

    }
}