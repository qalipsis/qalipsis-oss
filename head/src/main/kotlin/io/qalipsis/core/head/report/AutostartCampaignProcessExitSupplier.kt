/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.core.head.report

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.campaign.AutostartCampaignConfiguration
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.lifetime.ProcessExitCodeSupplier
import jakarta.inject.Singleton
import java.util.Optional

/**
 * Implementation of [ProcessExitCodeSupplier] to force a failing exit code when the campaign failed.
 *
 * The campaign execution errors are in the range 200.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(beans = [AutostartCampaignConfiguration::class])
class AutostartCampaignProcessExitSupplier(
    private val autostartCampaignConfiguration: AutostartCampaignConfiguration,
    private val campaignReportStateKeeper: CampaignReportStateKeeper,
) : ProcessExitCodeSupplier {

    override suspend fun await(): Optional<Int> {
        val report = campaignReportStateKeeper.generateReport(autostartCampaignConfiguration.generatedKey)
        val reportStatus = report?.status
        return if (reportStatus == null || reportStatus.exitCode < 0 || reportStatus == ExecutionStatus.SUCCESSFUL || reportStatus == ExecutionStatus.WARNING) {
            Optional.empty()
        } else {
            Optional.of(200 + reportStatus.exitCode)
        }
    }
}