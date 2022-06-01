package io.qalipsis.core.feedbacks

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Feedback to a [io.qalipsis.core.directives.FactoryAssignmentDirective].
 *
 * @property campaignKey campaign to which the feedback relates
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property error error message in case of failure
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("fa")
data class FactoryAssignmentFeedback(
    override val campaignKey: CampaignKey,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""
}


/**
 * Feedback to a [io.qalipsis.core.directives.ScenarioWarmUpDirective].
 *
 * @property campaignKey campaign to which the feedback relates
 * @property scenarioName scenario to which the feedback relates
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property error error message in case of failure
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("wup")
data class ScenarioWarmUpFeedback(
    override val campaignKey: CampaignKey,
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
@SerialName("eocsf")
data class EndOfCampaignScenarioFeedback(
    override val campaignKey: CampaignKey,
    val scenarioName: ScenarioName,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""
}


/**
 * Notification sent from the factory to the head, when the execution of all the scenarios is complete.
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("eocf")
data class EndOfCampaignFeedback(
    override val campaignKey: CampaignKey,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""
}

/**
 * Feedback to a [io.qalipsis.core.directives.CampaignScenarioShutdownDirective].
 *
 * @property campaignKey campaign to which the feedback relates
 * @property scenarioName scenario to which the feedback relates
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property error error message in case of failure
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("ssd")
data class CampaignScenarioShutdownFeedback(
    override val campaignKey: CampaignKey,
    val scenarioName: ScenarioName,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""
}

/**
 * Feedback to a [io.qalipsis.core.directives.CampaignShutdownDirective].
 *
 * @property campaignKey campaign to which the feedback relates
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property error error message in case of failure
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("csd")
data class CampaignShutdownFeedback(
    override val campaignKey: CampaignKey,
    override val status: FeedbackStatus,
    override val error: String? = null
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""
}

/**
 * Feedback to a [io.qalipsis.core.directives.CampaignAbortDirective].
 *
 * @property campaignKey campaign to which the feedback relates
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property error error message in case of failure
 * @property scenarioNames list of scenario names for which the campaign has to be aborted
 *
 * @author Svetlana Paliashchuk
 */
@Serializable
@SerialName("caf")
data class CampaignAbortFeedback(
    override val campaignKey: CampaignKey,
    override val status: FeedbackStatus,
    override val error: String? = null,
    val scenarioNames: List<ScenarioName>
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""
}