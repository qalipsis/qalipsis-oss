package io.qalipsis.core.feedbacks

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Feedback to a [io.qalipsis.core.directives.MinionsAssignmentDirective].
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
@SerialName("ma")
data class MinionsAssignmentFeedback(
    override val campaignId: CampaignId,
    val scenarioId: ScenarioId,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

}

/**
 * Feedback to a [io.qalipsis.core.directives.MinionsStartDirective].
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
@SerialName("ms")
data class MinionsStartFeedback(
    override val campaignId: CampaignId,
    val scenarioId: ScenarioId,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

}

/**
 * Notification sent from the factory to the head, when the execution of all the minions under load of a scenario
 * is complete.
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("cmf")
data class CompleteMinionFeedback(
    override val campaignId: CampaignId,
    val scenarioId: ScenarioId,
    val minionId: MinionId,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

}

/**
 * Feedback to a [io.qalipsis.core.directives.MinionsStartDirective].
 *
 * @property key unique key of the feedback
 * @property campaignId campaign to which the feedback relates
 * @property scenarioId scenario to which the feedback relates
 * @property minionIds IDs of all the minions to shut down
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property error error message in case of failure
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("msd")
data class MinionsShutdownFeedback(
    override val campaignId: CampaignId,
    val scenarioId: ScenarioId,
    val minionIds: List<MinionId>,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

}
