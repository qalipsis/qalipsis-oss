/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

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
 * @property errorMessage error message in case of failure
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("fa")
data class FactoryAssignmentFeedback(
    override val campaignKey: CampaignKey,
    override val status: FeedbackStatus,
    private val errorMessage: String = ""
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""

    override val error: String?
        get() = errorMessage.takeIf { it.isNotBlank() }
}


/**
 * Feedback to a [io.qalipsis.core.directives.ScenarioWarmUpDirective].
 *
 * @property campaignKey campaign to which the feedback relates
 * @property scenarioName scenario to which the feedback relates
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property errorMessage error message in case of failure
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("wup")
data class ScenarioWarmUpFeedback(
    override val campaignKey: CampaignKey,
    val scenarioName: ScenarioName,
    override val status: FeedbackStatus,
    private val errorMessage: String = ""
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""

    override val error: String?
        get() = errorMessage.takeIf { it.isNotBlank() }
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
    private val errorMessage: String = ""
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""

    override val error: String?
        get() = errorMessage.takeIf { it.isNotBlank() }
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
    private val errorMessage: String = ""
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""

    override val error: String?
        get() = errorMessage.takeIf { it.isNotBlank() }
}

/**
 * Feedback to a [io.qalipsis.core.directives.CampaignScenarioShutdownDirective].
 *
 * @property campaignKey campaign to which the feedback relates
 * @property scenarioName scenario to which the feedback relates
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property errorMessage error message in case of failure
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("ssd")
data class CampaignScenarioShutdownFeedback(
    override val campaignKey: CampaignKey,
    val scenarioName: ScenarioName,
    override val status: FeedbackStatus,
    private val errorMessage: String = ""
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""

    override val error: String?
        get() = errorMessage.takeIf { it.isNotBlank() }
}

/**
 * Feedback to a [io.qalipsis.core.directives.CampaignShutdownDirective].
 *
 * @property campaignKey campaign to which the feedback relates
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property errorMessage error message in case of failure
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("csd")
data class CampaignShutdownFeedback(
    override val campaignKey: CampaignKey,
    override val status: FeedbackStatus,
    private val errorMessage: String = ""
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""

    override val error: String?
        get() = errorMessage.takeIf { it.isNotBlank() }
}

/**
 * Feedback to a [io.qalipsis.core.directives.CampaignAbortDirective].
 *
 * @property campaignKey campaign to which the feedback relates
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property errorMessage error message in case of failure
 * @property scenarioNames list of scenario names for which the campaign has to be aborted
 *
 * @author Svetlana Paliashchuk
 */
@Serializable
@SerialName("caf")
data class CampaignAbortFeedback(
    override val campaignKey: CampaignKey,
    override val status: FeedbackStatus,
    private val errorMessage: String = "",
    val scenarioNames: List<ScenarioName>
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""
    override val error: String?
        get() = errorMessage.takeIf { it.isNotBlank() }
}

/**
 * Feedback to a [io.qalipsis.core.directives.FactoryAssignmentDirective].
 *
 * @property campaignKey campaign to which the feedback relates
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property errorMessage error message in case of failure
 * @property hard if the running campaign should be aborted hardly (generating a failure) or not.
 *
 * @author Francisca Eze
 */
@Serializable
@SerialName("ctf")
data class CampaignTimeoutFeedback(
    override val campaignKey: CampaignKey,
    override val status: FeedbackStatus,
    private val errorMessage: String = "",
    val hard: Boolean
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""

    override val error: String?
        get() = errorMessage.takeIf { it.isNotBlank() }
}