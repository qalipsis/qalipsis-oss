package io.qalipsis.core.feedbacks

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.ScenarioId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Feedback to a [io.qalipsis.core.directives.FactoryAssignmentDirective].
 *
 * @property key unique key of the feedback
 * @property campaignId campaign to which the feedback relates
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property error error message in case of failure
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("fa")
data class FactoryAssignmentFeedback(
    override val key: FeedbackKey,
    override val campaignId: CampaignId,
    override val nodeId: String,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback

/**
 * Feedback to a [io.qalipsis.core.directives.ScenarioWarmUpDirective].
 *
 * @property key unique key of the feedback
 * @property campaignId campaign to which the feedback relates
 * @property scenarioId scenario to which the feedback relates
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property error error message in case of failure
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("wup")
data class ScenarioWarmUpFeedback(
    override val key: FeedbackKey,
    override val campaignId: CampaignId,
    val scenarioId: ScenarioId,
    override val nodeId: String,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback


/**
 * Notification sent from the factory to the head, when the execution of all the minions under load of a scenario
 * is complete.
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("eocsf")
data class EndOfCampaignScenarioFeedback(
    override val key: FeedbackKey,
    override val campaignId: CampaignId,
    val scenarioId: ScenarioId,
    override val nodeId: String,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback


/**
 * Notification sent from the factory to the head, when the execution of all the scenarios is complete.
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("eocf")
data class EndOfCampaignFeedback(
    override val key: FeedbackKey,
    override val campaignId: CampaignId,
    override val nodeId: String,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback

/**
 * Feedback to a [io.qalipsis.core.directives.CampaignScenarioShutdownDirective].
 *
 * @property key unique key of the feedback
 * @property campaignId campaign to which the feedback relates
 * @property scenarioId scenario to which the feedback relates
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property error error message in case of failure
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("ssd")
data class CampaignScenarioShutdownFeedback(
    override val key: FeedbackKey,
    override val campaignId: CampaignId,
    val scenarioId: ScenarioId,
    override val nodeId: String,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback

/**
 * Feedback to a [io.qalipsis.core.directives.CampaignShutdownDirective].
 *
 * @property key unique key of the feedback
 * @property campaignId campaign to which the feedback relates
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property error error message in case of failure
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("csd")
data class CampaignShutdownFeedback(
    override val key: FeedbackKey,
    override val campaignId: CampaignId,
    override val nodeId: String,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback