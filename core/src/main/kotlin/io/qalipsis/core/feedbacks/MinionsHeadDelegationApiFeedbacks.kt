package io.qalipsis.core.feedbacks

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.ScenarioId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Feedback to a [io.qalipsis.core.directives.MinionsDeclarationDirective].
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
@SerialName("md")
data class MinionsDeclarationFeedback(
    override val campaignId: CampaignId,
    val scenarioId: ScenarioId,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""
}

/**
 * Feedback to a [io.qalipsis.core.directives.MinionsRampUpPreparationDirective].
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
@SerialName("mrp")
data class MinionsRampUpPreparationFeedback(
    override val campaignId: CampaignId,
    val scenarioId: ScenarioId,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

}
