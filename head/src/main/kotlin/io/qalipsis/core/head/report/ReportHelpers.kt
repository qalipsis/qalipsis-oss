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

package io.qalipsis.core.head.report

import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ScenarioReport
import io.qalipsis.core.head.model.CampaignExecutionDetails
import io.qalipsis.core.head.model.Scenario
import io.qalipsis.core.head.model.ScenarioExecutionDetails
import java.time.Instant

/**
 * Consolidates a collection of [ScenarioReportingExecutionState] into a [CampaignReport].
 *
 * @author Eric Jessé
 */
internal fun Collection<ScenarioReport>.toCampaignReport(): CampaignReport {

    return CampaignReport(
        campaignKey = first().campaignKey,
        start = this.asSequence().mapNotNull { it.start }.minOrNull(),
        end = this.asSequence().mapNotNull { it.end }.maxOrNull(),
        scheduledMinions = null,
        startedMinions = this.asSequence().mapNotNull { it.startedMinions }.sum(),
        completedMinions = this.asSequence().mapNotNull { it.completedMinions }.sum(),
        successfulExecutions = this.asSequence().mapNotNull { it.successfulExecutions }.sum(),
        failedExecutions = this.asSequence().mapNotNull { it.failedExecutions }.sum(),
        status = when {
            any { it.status == ExecutionStatus.ABORTED } -> ExecutionStatus.ABORTED
            any { it.status == ExecutionStatus.FAILED } -> ExecutionStatus.FAILED
            any { it.status == ExecutionStatus.WARNING } -> ExecutionStatus.WARNING
            none { it.start != null } -> ExecutionStatus.QUEUED
            any { it.end == null } -> ExecutionStatus.QUEUED
            else -> ExecutionStatus.SUCCESSFUL
        },
        scenariosReports = toList()
    )
}

/**
 * Consolidates a collection of [ScenarioReportingExecutionState] into a [CampaignExecutionDetails].
 *
 * @author Eric Jessé
 */
internal fun Collection<ScenarioReport>.toCampaignExecutionDetails(): CampaignExecutionDetails {

    return CampaignExecutionDetails(
        creation = Instant.now(),
        version = Instant.now(),
        key = first().campaignKey,
        name = first().campaignKey,
        start = this.asSequence().mapNotNull { it.start }.minOrNull(),
        end = this.asSequence().mapNotNull { it.end }.maxOrNull(),
        scheduledMinions = null,
        speedFactor = 1.0,
        startedMinions = this.asSequence().mapNotNull { it.startedMinions }.sum(),
        completedMinions = this.asSequence().mapNotNull { it.completedMinions }.sum(),
        successfulExecutions = this.asSequence().mapNotNull { it.successfulExecutions }.sum(),
        failedExecutions = this.asSequence().mapNotNull { it.failedExecutions }.sum(),
        status = when {
            any { it.status == ExecutionStatus.ABORTED } -> ExecutionStatus.ABORTED
            any { it.status == ExecutionStatus.FAILED } -> ExecutionStatus.FAILED
            any { it.status == ExecutionStatus.WARNING } -> ExecutionStatus.WARNING
            none { it.start == null } -> ExecutionStatus.QUEUED
            any { it.end == null } -> ExecutionStatus.IN_PROGRESS
            else -> ExecutionStatus.SUCCESSFUL
        },
        scenarios = this.map {
            Scenario(
                version = Instant.now(),
                name = it.scenarioName,
                minionsCount = it.startedMinions ?: 0
            )
        },
        scenariosReports = this.map {
            ScenarioExecutionDetails(
                id = it.scenarioName,
                name = it.scenarioName,
                start = it.start,
                end = it.end,
                startedMinions = it.startedMinions,
                completedMinions = it.completedMinions,
                successfulExecutions = it.successfulExecutions,
                failedExecutions = it.failedExecutions,
                status = when {
                    it.start == null -> ExecutionStatus.QUEUED
                    it.end == null -> ExecutionStatus.IN_PROGRESS
                    else -> it.status
                },
                messages = it.messages
            )
        }
    )
}


