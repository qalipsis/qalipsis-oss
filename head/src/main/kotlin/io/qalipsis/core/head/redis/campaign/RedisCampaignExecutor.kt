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

@file:OptIn(ExperimentalLettuceCoroutinesApi::class)

package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.AbstractCampaignExecutor
import io.qalipsis.core.head.campaign.CampaignConstraintsProvider
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.campaign.ChannelNameFactory
import io.qalipsis.core.head.campaign.states.AbstractCampaignExecutionState
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.communication.HeadChannel
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.hook.CampaignHook
import io.qalipsis.core.head.lock.LockProvider
import io.qalipsis.core.head.model.Factory
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope

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
internal class RedisCampaignExecutor(
    headChannel: HeadChannel,
    factoryService: FactoryService,
    campaignService: CampaignService,
    campaignReportStateKeeper: CampaignReportStateKeeper,
    campaignConstraintsProvider: CampaignConstraintsProvider,
    campaignHooks: Collection<CampaignHook>,
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) coroutineScope: CoroutineScope,
    private val campaignExecutionContext: CampaignExecutionContext,
    private val redisOperations: CampaignRedisOperations,
    lockProvider: LockProvider,
    channelNameFactory: ChannelNameFactory
) : AbstractCampaignExecutor<CampaignExecutionContext>(
    headChannel,
    factoryService,
    campaignService,
    campaignReportStateKeeper,
    coroutineScope,
    campaignExecutionContext,
    campaignConstraintsProvider,
    campaignHooks,
    lockProvider,
    channelNameFactory
) {

    @LogInputAndOutput
    override suspend fun createInitialState(
        campaign: RunningCampaign,
        factories: Collection<Factory>,
        scenarios: List<ScenarioSummary>
    ): CampaignExecutionState<CampaignExecutionContext> =
        RedisFactoryAssignmentState(campaign, factories, scenarios, redisOperations)

    @LogInput
    override suspend fun get(
        tenant: String,
        campaignKey: CampaignKey
    ): CampaignExecutionState<CampaignExecutionContext> {
        val currentState = redisOperations.getState(tenant, campaignKey)
        val executionState = resolveState(currentState, campaignKey, tenant)
        executionState.inject(campaignExecutionContext)

        // Since the state is rebuilt, it is marked as already initialized.
        executionState.initialized = true

        return executionState
    }

    protected fun resolveState(
        currentState: Pair<RunningCampaign, CampaignRedisState>?,
        campaignKey: CampaignKey,
        tenant: String
    ): AbstractCampaignExecutionState<CampaignExecutionContext> {
        val executionState = when (currentState?.second) {
            CampaignRedisState.FACTORY_DAGS_ASSIGNMENT_STATE -> RedisFactoryAssignmentState(
                campaign = currentState.first,
                operations = redisOperations
            )

            CampaignRedisState.MINIONS_ASSIGNMENT_STATE -> RedisMinionsAssignmentState(
                currentState.first,
                redisOperations
            )

            CampaignRedisState.WARMUP_STATE -> RedisWarmupState(currentState.first, redisOperations)
            CampaignRedisState.MINIONS_STARTUP_STATE -> RedisMinionsScheduleRampUpState(
                currentState.first,
                redisOperations
            )

            CampaignRedisState.RUNNING_STATE -> RedisRunningState(currentState.first, redisOperations)
            CampaignRedisState.COMPLETION_STATE -> RedisCompletionState(currentState.first, redisOperations)
            CampaignRedisState.FAILURE_STATE -> RedisFailureState(currentState.first, redisOperations)
            CampaignRedisState.ABORTING_STATE -> RedisAbortingState(currentState.first, redisOperations)
            else -> error("The state of the campaign $campaignKey of tenant $tenant is unidentified: $currentState")
        }

        return executionState
    }

    /**
     * Nothing to do, since the
     */
    override suspend fun set(state: CampaignExecutionState<CampaignExecutionContext>) = Unit

}