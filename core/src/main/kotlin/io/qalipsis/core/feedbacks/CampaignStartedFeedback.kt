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
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.ScenarioName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Notification sent from the factory to the head, to notify that the campaign was started for a DAG.
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("csfd")
data class CampaignStartedForDagFeedback(
    val campaignKey: CampaignKey,
    val scenarioName: ScenarioName,
    val dagId: DirectedAcyclicGraphName,
    /**
     * Status of the directive processing.
     */
    val status: FeedbackStatus,
    /**
     * Error message.
     */
    val error: String? = null
) : Feedback()
