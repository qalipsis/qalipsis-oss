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

import io.qalipsis.api.report.CampaignReportPublisher
import io.qalipsis.core.head.campaign.CampaignAutoStarter
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.communication.HeadChannel
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.hook.CampaignHook
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.head.orchestration.FactoryWorkflowAssignmentResolver
import jakarta.inject.Singleton
import javax.annotation.Nullable

/**
 * Context containing the required components for the execution of a campaign state.
 *
 * @author Eric Jessé
 */
@Singleton
internal class CampaignExecutionContext(
    val campaignService: CampaignService,
    val factoryService: FactoryService,
    val campaignReportStateKeeper: CampaignReportStateKeeper,
    val headChannel: HeadChannel,
    val assignmentResolver: FactoryWorkflowAssignmentResolver,
    val reportPublishers: Collection<CampaignReportPublisher>,
    @Nullable val campaignAutoStarter: CampaignAutoStarter?,
    val campaignHooks: Collection<CampaignHook>
    )