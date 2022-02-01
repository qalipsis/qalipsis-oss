package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.core.head.campaign.CampaignConfiguration
import io.qalipsis.core.head.model.NodeId
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@ExperimentalLettuceCoroutinesApi
@Singleton
internal class CampaignRedisOperations(
    private val redisCommands: RedisCoroutinesCommands<String, String>
) {

    suspend fun saveConfiguration(campaign: CampaignConfiguration) {
        redisCommands.hset(
            "campaign-management:{${campaign.id}}",
            mapOf("configuration" to Json.encodeToString(campaign))
        )
    }

    /**
     * Creates brand new feedback expectations initialized with the collection of factories identifiers.
     */
    suspend fun prepareFactoriesForFeedbackExpectations(campaign: CampaignConfiguration) {
        val key = buildExpectedFeedbackKey(campaign.id)
        redisCommands.unlink(key)
        if (campaign.factories.isNotEmpty()) {
            redisCommands.sadd(key, *campaign.factories.keys.toTypedArray())
        }
    }

    /**
     * Creates brand new feedback expectations initialized with the scenarios for each factory.
     */
    suspend fun prepareAssignmentsForFeedbackExpectations(campaign: CampaignConfiguration) {
        prepareFactoriesForFeedbackExpectations(campaign)
        campaign.factories.forEach { (factory, config) ->
            val key = buildFactoryAssignmentFeedbackKey(campaign.id, factory)
            redisCommands.unlink(key)
            if (config.assignment.isNotEmpty()) {
                redisCommands.sadd(key, *config.assignment.keys.toTypedArray())
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
        redisCommands.srem(feedbackKey, factory)
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
        redisCommands.srem(feedbackKey, scenarioId)
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
        redisCommands.hset("campaign-management:{$campaignId}", mapOf("state" to "$state"))
    }

    /**
     * Fetches the current state of the campaign.
     */
    suspend fun getState(campaignId: CampaignId): Pair<CampaignConfiguration, CampaignRedisState>? {
        val campaignDetails = mutableMapOf<String, String>()
        redisCommands.hgetall("campaign-management:{$campaignId}")
            .collect { campaignDetails[it.key] = it.value }
        val state = campaignDetails["state"]?.let { CampaignRedisState.valueOf(it) }
        val campaign = campaignDetails["configuration"]?.let { Json.decodeFromString<CampaignConfiguration>(it) }
        return campaign?.let { it to state!! }
    }

    /**
     * Cleans all the data used for the campaign.
     */
    suspend fun clean(campaign: CampaignConfiguration) {
        redisCommands.unlink("campaign-management:{${campaign.id}}")
        redisCommands.unlink(buildExpectedFeedbackKey(campaign.id))
        campaign.factories.keys.forEach { factory ->
            redisCommands.unlink(buildFactoryAssignmentFeedbackKey(campaign.id, factory))
        }
    }

    private fun buildExpectedFeedbackKey(campaignId: CampaignId) = "{campaign-management:{$campaignId}:feedback"

    private fun buildFactoryAssignmentFeedbackKey(campaignId: CampaignId, factory: NodeId) =
        "campaign-management:{$campaignId}:factory:feedback:$factory"

    private suspend fun exists(key: String): Boolean {
        return (redisCommands.exists(key) ?: 0L) > 0L
    }
}