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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Notification sent from the factory to the head, when the execution of all the scenarios is complete.
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("fail")
data class FailedCampaignFeedback(
    override val campaignKey: CampaignKey,
    override val error: String
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override var tenant: String = ""

    override val status: FeedbackStatus = FeedbackStatus.FAILED

}
