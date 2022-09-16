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
 * Parent class of all implementations of [CampaignExecutionState].
 *
 * @author Eric Jessé
 */
internal abstract class AbstractCampaignExecutionState<C : CampaignExecutionContext>(
    override val campaignKey: CampaignKey
) : CampaignExecutionState<C> {

    var initialized: Boolean = false

    protected lateinit var context: C

    override val isCompleted: Boolean = false

    override fun inject(context: C) {
        this.context = context
    }

    override suspend fun init(): List<Directive> {
        return if (!initialized) {
            val directives = doInit()
            initialized = true
            directives
        } else {
            emptyList()
        }
    }

    protected open suspend fun doInit(): List<Directive> = emptyList()

    override suspend fun process(feedback: Feedback): CampaignExecutionState<C> {
        return doTransition(feedback)
    }

    protected open suspend fun doTransition(feedback: Feedback): CampaignExecutionState<C> {
        return this
    }

    override suspend fun abort(abortConfiguration: AbortRunningCampaign): CampaignExecutionState<C> {
        return this
    }
}