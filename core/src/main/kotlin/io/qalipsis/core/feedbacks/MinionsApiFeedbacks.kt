package io.qalipsis.core.feedbacks

import io.qalipsis.api.context.CampaignName
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Feedback to a [io.qalipsis.core.directives.MinionsAssignmentDirective].
 *
 * @property key unique key of the feedback
 * @property campaignName campaign to which the feedback relates
 * @property scenarioName scenario to which the feedback relates
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property error error message in case of failure
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("ma")
data class MinionsAssignmentFeedback(
    override val campaignName: CampaignName,
    val scenarioName: ScenarioName,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""
}

/**
 * Feedback to a [io.qalipsis.core.directives.MinionsStartDirective].
 *
 * @property key unique key of the feedback
 * @property campaignName campaign to which the feedback relates
 * @property scenarioName scenario to which the feedback relates
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property error error message in case of failure
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("ms")
data class MinionsStartFeedback(
    override val campaignName: CampaignName,
    val scenarioName: ScenarioName,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""
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
    override val campaignName: CampaignName,
    val scenarioName: ScenarioName,
    val minionId: MinionId,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""
}

/**
 * Feedback to a [io.qalipsis.core.directives.MinionsStartDirective].
 *
 * @property key unique key of the feedback
 * @property campaignName campaign to which the feedback relates
 * @property scenarioName scenario to which the feedback relates
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
    override val campaignName: CampaignName,
    val scenarioName: ScenarioName,
    val minionIds: List<MinionId>,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""
}
