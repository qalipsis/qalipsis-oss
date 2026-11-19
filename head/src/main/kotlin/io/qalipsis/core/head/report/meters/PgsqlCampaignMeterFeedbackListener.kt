/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.report.meters

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.transaction.jdbc.DelegatingDataSource
import io.qalipsis.api.config.MetersConfig.EXPORT_CONFIGURATION
import io.qalipsis.core.configuration.ExecutionEnvironments.TRANSIENT
import jakarta.inject.Named
import jakarta.inject.Singleton
import javax.sql.DataSource

@Singleton
@Requirements(
    Requires(notEnv = [TRANSIENT]),
    Requires(property = "${EXPORT_CONFIGURATION}.default.enabled", notEquals = "false")
)
internal class PgsqlCampaignMeterFeedbackListener(
    @Named("default") injectedDataSource: DataSource,
    campaignOwnershipChecker: CampaignOwnershipChecker
) : AbstractCampaignMeterFeedbackListener(campaignOwnershipChecker) {

    override val dataSource: DataSource = DelegatingDataSource.unwrapDataSource(injectedDataSource)
}
