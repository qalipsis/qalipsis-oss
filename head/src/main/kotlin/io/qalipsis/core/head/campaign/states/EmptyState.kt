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

internal object EmptyState : CampaignExecutionState<CampaignExecutionContext> {

    override val isCompleted: Boolean = true

    override val campaignKey: CampaignKey = ""

    override fun inject(context: CampaignExecutionContext) = Unit

    override suspend fun init(): List<Directive> = emptyList()

    override suspend fun process(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        throw IllegalStateException()
    }

    override suspend fun abort(abortConfiguration: AbortRunningCampaign): CampaignExecutionState<CampaignExecutionContext> {
        return this
    }
}