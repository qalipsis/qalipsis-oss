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

package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.Feedback

/**
 * State of a campaign execution.
 *
 * @param C The type of the context the state requires for its execution.
 *
 * @author Eric Jessé
 */
internal interface CampaignExecutionState<C : CampaignExecutionContext> {

    /**
     * Specifies whether the current state is a completion state and has no other next than itself.
     */
    val isCompleted: Boolean

    /**
     * ID of the current campaign.
     */
    val campaignKey: CampaignKey

    fun inject(context: C)

    /**
     * Performs the operations implies by the creation of the state.
     *
     * @return the directives to publish after the state was initialized
     */
    suspend fun init(): List<Directive>

    /**
     * Processes the received [Feedback] and returns the resulting [CampaignExecutionState].
     *
     * @return the state consecutive to the processing of the feedback on this state
     */
    suspend fun process(feedback: Feedback): CampaignExecutionState<C>

    /**
     * Receives [AbortRunningCampaign] and returns the [AbortingState].
     *
     * @return the [AbortingState] with abortConfiguration and error message
     */
    suspend fun abort(abortConfiguration: AbortRunningCampaign): CampaignExecutionState<C>

}
