package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisHashCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisKeyCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisSetCoroutinesCommands
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.context.CampaignKey
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
            "campaign-management:{${campaign.tenant}:${campaign.key}}",
            mapOf("configuration" to Json.encodeToString(campaign))
        )
    }

    /**
     * Creates brand new feedback expectations initialized with the collection of factories identifiers.
     */
    suspend fun prepareFactoriesForFeedbackExpectations(campaign: CampaignConfiguration) {
        val key = buildExpectedFeedbackKey(campaign.tenant, campaign.key)
        redisKeyCommands.unlink(key)
        if (campaign.factories.isNotEmpty()) {
            redisSetCommands.sadd(key, *campaign.factories.keys.toTypedArray())
        }
    }

    /**
     * Creates brand new feedback expectations initialized with the collection of scenarios identifiers.
     */
    suspend fun prepareScenariosForFeedbackExpectations(campaign: CampaignConfiguration) {
        val key = buildExpectedFeedbackKey(campaign.tenant, campaign.key)
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
            val key = buildFactoryAssignmentFeedbackKey(campaign.tenant, campaign.key, factory)
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
    suspend fun markFeedbackForFactory(tenant: String, campaignKey: CampaignKey, factory: NodeId): Boolean {
        val feedbackKey = buildExpectedFeedbackKey(tenant, campaignKey)
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
        campaignKey: CampaignKey,
        scenarioName: ScenarioName
    ): Boolean {
        val feedbackKey = buildExpectedFeedbackKey(tenant, campaignKey)
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
        campaignKey: CampaignKey,
        factory: NodeId,
        scenarioName: ScenarioName
    ): Boolean {
        val feedbackKey = buildFactoryAssignmentFeedbackKey(tenant, campaignKey, factory)
        redisSetCommands.srem(feedbackKey, scenarioName)
        return if (!exists(feedbackKey)) {
            markFeedbackForFactory(tenant, campaignKey, factory)
        } else {
            false
        }
    }

    /**
     * Updates the state of the campaign.
     */
    suspend fun setState(tenant: String, campaignKey: CampaignKey, state: CampaignRedisState) {
        redisHashCommands.hset("campaign-management:{$tenant:$campaignKey}", mapOf("state" to "$state"))
    }

    /**
     * Fetches the current state of the campaign.
     */
    suspend fun getState(tenant: String, campaignKey: CampaignKey): Pair<CampaignConfiguration, CampaignRedisState>? {
        val campaignDetails = mutableMapOf<String, String>()
        redisHashCommands.hgetall("campaign-management:{$tenant:$campaignKey}")
            .collect { campaignDetails[it.key] = it.value }
        val state = campaignDetails["state"]?.let { CampaignRedisState.valueOf(it) }
        val campaign = campaignDetails["configuration"]?.let { Json.decodeFromString<CampaignConfiguration>(it) }
        return campaign?.let { it to state!! }
    }

    /**
     * Cleans all the data used for the campaign.
     */
    suspend fun clean(campaign: CampaignConfiguration) {
        redisKeyCommands.unlink("campaign-management:{${campaign.tenant}:${campaign.key}}")
        redisKeyCommands.unlink(buildExpectedFeedbackKey(campaign.tenant, campaign.key))
        campaign.factories.keys.forEach { factory ->
            redisKeyCommands.unlink(buildFactoryAssignmentFeedbackKey(campaign.tenant, campaign.key, factory))
        }
    }

    private fun buildExpectedFeedbackKey(tenant: String, campaignKey: CampaignKey) =
        "{campaign-management:{${tenant}:$campaignKey}:feedback"

    private fun buildFactoryAssignmentFeedbackKey(tenant: String, campaignKey: CampaignKey, factory: NodeId) =
        "campaign-management:{$tenant:$campaignKey}:factory:feedback:$factory"

    private suspend fun exists(key: String): Boolean {
        return (redisKeyCommands.exists(key) ?: 0L) > 0L
    }
}