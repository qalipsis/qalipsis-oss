package io.qalipsis.core.feedbacks

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Feedback to a [io.qalipsis.core.directives.MinionsDeclarationDirective].
 *
 * @property key unique key of the feedback
 * @property campaignKey campaign to which the feedback relates
 * @property scenarioName scenario to which the feedback relates
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property error error message in case of failure
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("md")
data class MinionsDeclarationFeedback(
    override val campaignKey: CampaignKey,
    val scenarioName: ScenarioName,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""
}

/**
 * Feedback to a [io.qalipsis.core.directives.MinionsRampUpPreparationDirective].
 *
 * @property key unique key of the feedback
 * @property campaignKey campaign to which the feedback relates
 * @property scenarioName scenario to which the feedback relates
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property error error message in case of failure
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("mrp")
data class MinionsRampUpPreparationFeedback(
    override val campaignKey: CampaignKey,
    val scenarioName: ScenarioName,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""
}
