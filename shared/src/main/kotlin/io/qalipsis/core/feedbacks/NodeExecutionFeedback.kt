/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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
 * Notification sent when a factory was successfully started but later fails.
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("nodexec")
data class NodeExecutionFeedback(
    override var tenant: String,
    override val campaignKey: CampaignKey,
    private val errorMessage: String,
    override var nodeId: String = "",
    override val status: FeedbackStatus = FeedbackStatus.FAILED
) : Feedback(), CampaignManagementFeedback {

    override val error: String?
        get() = errorMessage.takeIf { it.isNotBlank() }

}
