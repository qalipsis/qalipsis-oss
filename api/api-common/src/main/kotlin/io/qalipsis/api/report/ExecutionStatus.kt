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

package io.qalipsis.api.report

import io.qalipsis.api.report.ExecutionStatus.ABORTED
import io.qalipsis.api.report.ExecutionStatus.FAILED
import io.qalipsis.api.report.ExecutionStatus.IN_PROGRESS
import io.qalipsis.api.report.ExecutionStatus.QUEUED
import io.qalipsis.api.report.ExecutionStatus.SCHEDULED
import io.qalipsis.api.report.ExecutionStatus.SUCCESSFUL
import io.qalipsis.api.report.ExecutionStatus.WARNING


/**
 * Execution status of a [ScenarioReport] or [CampaignReport].
 *
 * @property SUCCESSFUL all the steps, were successful
 * @property WARNING a deeper look at the reports is required, but the campaign does not fail
 * @property FAILED the campaign went until the end, but got errors
 * @property ABORTED the campaign was aborted, either by a user or a critical failure
 * @property SCHEDULED the campaign is scheduled for a later point in time
 * @property QUEUED the campaign is being prepared and will start very soon
 * @property IN_PROGRESS the campaign is currently running
 *
 * @author Eric Jessé
 */
enum class ExecutionStatus(val exitCode: Int) {
    SUCCESSFUL(0),
    WARNING(0),
    FAILED(1),
    ABORTED(2),
    SCHEDULED(-1),
    QUEUED(-1),
    IN_PROGRESS(-1)
}
