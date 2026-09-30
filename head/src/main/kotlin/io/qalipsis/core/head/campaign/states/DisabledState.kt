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
        log.trace { "Enriching the campaign $campaignKey" }
        context.campaignService.enrich(campaign)
        log.trace { "Releasing the factories for the campaign $campaignKey" }
        context.factoryService.releaseFactories(campaign, campaign.factories.keys)
        log.trace { "Unsubscribe from feedback channel for the campaign $campaignKey" }
        context.headChannel.unsubscribeFeedback(campaign.feedbackChannel)

        if (context.reportPublishers.isNotEmpty()) {
            log.trace { "Aggregating the in-memory report for the campaign $campaignKey" }
            context.campaignReportStateKeeper.generateReport(campaignKey)?.let { report ->
                log.trace { "Publishable report for the campaign $campaignKey: $report" }
                context.reportPublishers.sortedBy { it.order }.forEach { publisher ->
                    log.debug { "Publishing the report of the campaign $campaignKey by $publisher" }
                    tryAndLogOrNull(log) {
                        publisher.publish(campaign.tenant, campaign.key, report)
                    }
                }
            }
        }
        context.campaignHooks.forEach { hook ->
            log.trace { "Calling hook on $hook $campaignKey" }
            hook.afterStop(campaignKey)
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