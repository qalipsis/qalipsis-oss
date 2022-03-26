package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisHashCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisKeyCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisSetCoroutinesCommands
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioId
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
            "campaign-management:{${campaign.id}}",
            mapOf("configuration" to Json.encodeToString(campaign))
        )
    }

    /**
     * Creates brand new feedback expectations initialized with the collection of factories identifiers.
     */
    suspend fun prepareFactoriesForFeedbackExpectations(campaign: CampaignConfiguration) {
        val key = buildExpectedFeedbackKey(campaign.id)
        redisKeyCommands.unlink(key)
        if (campaign.factories.isNotEmpty()) {
            redisSetCommands.sadd(key, *campaign.factories.keys.toTypedArray())
        }
    }

    /**
     * Creates brand new feedback expectations initialized with the collection of scenarios identifiers.
     */
    suspend fun prepareScenariosForFeedbackExpectations(campaign: CampaignConfiguration) {
        val key = buildExpectedFeedbackKey(campaign.id)
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
            val key = buildFactoryAssignmentFeedbackKey(campaign.id, factory)
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
    suspend fun markFeedbackForFactory(campaignId: CampaignId, factory: NodeId): Boolean {
        val feedbackKey = buildExpectedFeedbackKey(campaignId)
        redisSetCommands.srem(feedbackKey, factory)
        return !exists(feedbackKey)
    }

    /**
     * Removes the scenarioId from the feedback expectations and returns whether the expectations are empty or not.
     *
     * @return true when there are no longer expectations, false otherwise
     */
    suspend fun markFeedbackForScenario(campaignId: CampaignId, scenarioId: ScenarioId): Boolean {
        val feedbackKey = buildExpectedFeedbackKey(campaignId)
        redisSetCommands.srem(feedbackKey, scenarioId)
        return !exists(feedbackKey)
    }

    /**
     * Removes the factory from the feedback expectations and returns whether the expectations are empty or not.
     *
     * @return true when there are no longer expectations, false otherwise
     */
    suspend fun markFeedbackForFactoryScenario(
        campaignId: CampaignId,
        factory: NodeId,
        scenarioId: ScenarioId
    ): Boolean {
        val feedbackKey = buildFactoryAssignmentFeedbackKey(campaignId, factory)
        redisSetCommands.srem(feedbackKey, scenarioId)
        return if (!exists(feedbackKey)) {
            markFeedbackForFactory(campaignId, factory)
        } else {
            false
        }
    }

    /**
     * Updates the state of the campaign.
     */
    suspend fun setState(campaignId: CampaignId, state: CampaignRedisState) {
        redisHashCommands.hset("campaign-management:{$campaignId}", mapOf("state" to "$state"))
    }

    /**
     * Fetches the current state of the campaign.
     */
    suspend fun getState(campaignId: CampaignId): Pair<CampaignConfiguration, CampaignRedisState>? {
        val campaignDetails = mutableMapOf<String, String>()
        redisHashCommands.hgetall("campaign-management:{$campaignId}")
            .collect { campaignDetails[it.key] = it.value }
        val state = campaignDetails["state"]?.let { CampaignRedisState.valueOf(it) }
        val campaign = campaignDetails["configuration"]?.let { Json.decodeFromString<CampaignConfiguration>(it) }
        return campaign?.let { it to state!! }
    }

    /**
     * Cleans all the data used for the campaign.
     */
    suspend fun clean(campaign: CampaignConfiguration) {
        redisKeyCommands.unlink("campaign-management:{${campaign.id}}")
        redisKeyCommands.unlink(buildExpectedFeedbackKey(campaign.id))
        campaign.factories.keys.forEach { factory ->
            redisKeyCommands.unlink(buildFactoryAssignmentFeedbackKey(campaign.id, factory))
        }
    }

    private fun buildExpectedFeedbackKey(campaignId: CampaignId) = "{campaign-management:{$campaignId}:feedback"

    private fun buildFactoryAssignmentFeedbackKey(campaignId: CampaignId, factory: NodeId) =
        "campaign-management:{$campaignId}:factory:feedback:$factory"

    private suspend fun exists(key: String): Boolean {
        return (redisKeyCommands.exists(key) ?: 0L) > 0L
    }
}