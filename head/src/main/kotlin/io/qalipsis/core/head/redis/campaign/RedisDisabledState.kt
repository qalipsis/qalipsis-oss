package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.head.campaign.states.DisabledState

@ExperimentalLettuceCoroutinesApi
internal class RedisDisabledState(
    campaign: RunningCampaign,
    isSuccessful: Boolean = true,
    private val operations: CampaignRedisOperations
) : DisabledState(campaign, isSuccessful) {

    override suspend fun doInit(): List<Directive> {
        operations.clean(campaign)
        return super.doInit()
    }
}