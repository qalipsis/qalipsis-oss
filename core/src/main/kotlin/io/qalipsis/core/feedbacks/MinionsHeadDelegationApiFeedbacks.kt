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
 * Feedback to a [io.qalipsis.core.directives.MinionsDeclarationDirective].
 *
 * @property key unique key of the feedback
 * @property campaignKey campaign to which the feedback relates
 * @property scenarioName scenario to which the feedback relates
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property errorMessage error message in case of failure
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("md")
data class MinionsDeclarationFeedback(
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
 * Feedback to a [io.qalipsis.core.directives.MinionsRampUpPreparationDirective].
 *
 * @property key unique key of the feedback
 * @property campaignKey campaign to which the feedback relates
 * @property scenarioName scenario to which the feedback relates
 * @property nodeId ID of the factory node that emitted the feedback
 * @property status status of the execution of the directive
 * @property errorMessage error message in case of failure
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("mrp")
data class MinionsRampUpPreparationFeedback(
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
