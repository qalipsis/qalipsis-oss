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

package io.qalipsis.core.head.report;

import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.ReportEntity
import io.qalipsis.core.head.jdbc.entity.ReportTaskEntity
import java.nio.file.Path

/**
 * Service to generate a pdf view from report details.
 *
 * @author Francisca Eze
 */
interface TemplateReportService {

    /**
     * Use the report information to generate a pdf file.
     *
     * @param report report entity for which to generate a report
     * @param campaignReportDetail contains all the necessary details for generating a report file
     * @param reportTask report task that references this report
     * @param creator requester of the report file generation
     * @param dataSeries collection of DataSeriesEntity related to current report generation
     * @param tenant Identifier of the tenant owning the report
     * @param reportTempDir absolute path of the temporal directory to store current report related files
     * @param campaignReferenceToName map of campaign references to campaign names
     *
     * @return byte array representing the generated pdf file
     */
    suspend fun generatePdf(
        report: ReportEntity,
        campaignReportDetail: CampaignReportDetail,
        reportTask: ReportTaskEntity,
        creator: String,
        dataSeries: Collection<DataSeriesEntity>,
        tenant: String,
        reportTempDir: Path,
        campaignReferenceToName: Map<String, String>
    ): ByteArray
}
