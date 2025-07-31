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

import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.directives.CompleteCampaignDirective
import io.qalipsis.core.directives.Directive

open class DisabledState(
    protected val campaign: RunningCampaign,
    private val isSuccessful: Boolean = true
) : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign.key) {

    override val isCompleted: Boolean = true

    override suspend fun doInit(): List<Directive> {
        context.campaignService.enrich(campaign)
        context.factoryService.releaseFactories(campaign, campaign.factories.keys)
        context.headChannel.unsubscribeFeedback(campaign.feedbackChannel)

        if (context.reportPublishers.isNotEmpty()) {
            context.campaignReportStateKeeper.generateReport(campaignKey)?.let { report ->
                context.reportPublishers.forEach { publisher ->
                    tryAndLogOrNull(log) {
                        publisher.publish(campaign.key, report)
                    }
                }
            }
        }
        context.campaignHooks.forEach {
            it.afterStop(campaignKey)
        }

        val directive = CompleteCampaignDirective(
            campaignKey = campaignKey,
            isSuccessful = isSuccessful,
            message = campaign.message,
            channel = campaign.broadcastChannel
        )
        context.campaignAutoStarter?.completeCampaign(directive)
        return listOf(directive)
    }

    override fun toString(): String {
        return "DisabledState(campaign=$campaign, isSuccessful=$isSuccessful, isCompleted=$isCompleted)"
    }

    private companion object {
        val log = logger()
    }
}