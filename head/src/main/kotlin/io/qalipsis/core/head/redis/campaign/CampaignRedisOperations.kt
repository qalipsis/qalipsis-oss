package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisHashCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisKeyCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisSetCoroutinesCommands
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.context.CampaignName
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioName
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@ExperimentalLettuceCoroutinesApi
@Singleton
internal class CampaignRedisOperations(
    private val redisKeyCommands: RedisKeyCoroutinesCommands<String, String>,
    private val redisSetCommands: RedisSetCoroutinesCommands<String, String>,
    private val redisHashCommands: RedisHashCoroutinesCommands<String, String>
) {

    suspend fun saveConfiguration(campaign: CampaignConfiguration) {
        redisHashCommands.hset(
            "campaign-management:{${campaign.tenant}:${campaign.name}}",
            mapOf("configuration" to Json.encodeToString(campaign))
        )
    }

    /**
     * Creates brand new feedback expectations initialized with the collection of factories identifiers.
     */
    suspend fun prepareFactoriesForFeedbackExpectations(campaign: CampaignConfiguration) {
        val key = buildExpectedFeedbackKey(campaign.tenant, campaign.name)
        redisKeyCommands.unlink(key)
        if (campaign.factories.isNotEmpty()) {
            redisSetCommands.sadd(key, *campaign.factories.keys.toTypedArray())
        }
    }

    /**
     * Creates brand new feedback expectations initialized with the collection of scenarios identifiers.
     */
    suspend fun prepareScenariosForFeedbackExpectations(campaign: CampaignConfiguration) {
        val key = buildExpectedFeedbackKey(campaign.tenant, campaign.name)
        redisKeyCommands.unlink(key)
        if (campaign.scenarios.isNotEmpty()) {
            redisSetCommands.sadd(key, *campaign.scenarios.keys.toTypedArray())
        }
    }

    /**
     * Creates brand new feedback expectations initialized with the scenarios for each factory.
     */
    suspend fun prepareAssignmentsForFeedbackExpectations(campaign: CampaignConfiguration) {
        prepareFactoriesForFeedbackExpectations(campaign)
        campaign.factories.forEach { (factory, config) ->
            val key = buildFactoryAssignmentFeedbackKey(campaign.tenant, campaign.name, factory)
            redisKeyCommands.unlink(key)
            if (config.assignment.isNotEmpty()) {
                redisSetCommands.sadd(key, *config.assignment.keys.toTypedArray())
            }
        }
    }

    /**
     * Removes the factory from the feedback expectations and returns whether the expectations are empty or not.
     *
     * @return true when there are no longer expectations, false otherwise
     */
    suspend fun markFeedbackForFactory(tenant: String, campaignName: CampaignName, factory: NodeId): Boolean {
        val feedbackKey = buildExpectedFeedbackKey(tenant, campaignName)
        redisSetCommands.srem(feedbackKey, factory)
        return !exists(feedbackKey)
    }

    /**
     * Removes the scenarioName from the feedback expectations and returns whether the expectations are empty or not.
     *
     * @return true when there are no longer expectations, false otherwise
     */
    suspend fun markFeedbackForScenario(
        tenant: String,
        campaignName: CampaignName,
        scenarioName: ScenarioName
    ): Boolean {
        val feedbackKey = buildExpectedFeedbackKey(tenant, campaignName)
        redisSetCommands.srem(feedbackKey, scenarioName)
        return !exists(feedbackKey)
    }

    /**
     * Removes the factory from the feedback expectations and returns whether the expectations are empty or not.
     *
     * @return true when there are no longer expectations, false otherwise
     */
    suspend fun markFeedbackForFactoryScenario(
        tenant: String,
        campaignName: CampaignName,
        factory: NodeId,
        scenarioName: ScenarioName
    ): Boolean {
        val feedbackKey = buildFactoryAssignmentFeedbackKey(tenant, campaignName, factory)
        redisSetCommands.srem(feedbackKey, scenarioName)
        return if (!exists(feedbackKey)) {
            markFeedbackForFactory(tenant, campaignName, factory)
        } else {
            false
        }
    }

    /**
     * Updates the state of the campaign.
     */
    suspend fun setState(tenant: String, campaignName: CampaignName, state: CampaignRedisState) {
        redisHashCommands.hset("campaign-management:{$tenant:$campaignName}", mapOf("state" to "$state"))
    }

    /**
     * Fetches the current state of the campaign.
     */
    suspend fun getState(tenant: String, campaignName: CampaignName): Pair<CampaignConfiguration, CampaignRedisState>? {
        val campaignDetails = mutableMapOf<String, String>()
        redisHashCommands.hgetall("campaign-management:{$tenant:$campaignName}")
            .collect { campaignDetails[it.key] = it.value }
        val state = campaignDetails["state"]?.let { CampaignRedisState.valueOf(it) }
        val campaign = campaignDetails["configuration"]?.let { Json.decodeFromString<CampaignConfiguration>(it) }
        return campaign?.let { it to state!! }
    }

    /**
     * Cleans all the data used for the campaign.
     */
    suspend fun clean(campaign: CampaignConfiguration) {
        redisKeyCommands.unlink("campaign-management:{${campaign.tenant}:${campaign.name}}")
        redisKeyCommands.unlink(buildExpectedFeedbackKey(campaign.tenant, campaign.name))
        campaign.factories.keys.forEach { factory ->
            redisKeyCommands.unlink(buildFactoryAssignmentFeedbackKey(campaign.tenant, campaign.name, factory))
        }
    }

    private fun buildExpectedFeedbackKey(tenant: String, campaignName: CampaignName) =
        "{campaign-management:{${tenant}:$campaignName}:feedback"

    private fun buildFactoryAssignmentFeedbackKey(tenant: String, campaignName: CampaignName, factory: NodeId) =
        "campaign-management:{$tenant:$campaignName}:factory:feedback:$factory"

    private suspend fun exists(key: String): Boolean {
        return (redisKeyCommands.exists(key) ?: 0L) > 0L
    }
}