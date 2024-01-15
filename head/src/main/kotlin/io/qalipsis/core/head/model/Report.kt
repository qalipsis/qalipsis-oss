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

package io.qalipsis.core.head.model

import io.micronaut.core.annotation.Introspected
import io.qalipsis.core.head.jdbc.repository.CampaignRepository.CampaignKeyAndName
import io.qalipsis.core.head.report.SharingMode
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

/**
 * External representation of a report.
 *
 * @author Joël Valère
 */

@Introspected
@Schema(
    name = "Report details",
    title = "Details of a report"
)
internal data class Report(
    @field:Schema(description = "Identifier of the report", required = true)
    val reference: String,

    @field:Schema(description = "Last update of the report", required = true)
    val version: Instant,

    @field:Schema(description = "The report's creator username", required = true)
    val creator: String,

    @field:Schema(description = "Display name of the report, should be unique into a tenant", required = true)
    @field:NotBlank
    @field:Size(min = 1, max = 200)
    val displayName: String,

    @field:Schema(description = "Description of the report, e.g. to explain its purpose", required = false)
    val description: String? = null,

    @field:Schema(description = "Sharing mode with the other members of the tenant", required = true)
    val sharingMode: SharingMode = SharingMode.READONLY,

    @field:Schema(
        description = "List of campaign keys to be included in the report",
        required = false
    )
    val campaignKeys: List<String> = emptyList(),

    @field:Schema(
        description = "List of campaign names patterns to be included in the report",
        required = false
    )
    val campaignNamesPatterns: List<String> = emptyList(),

    @field:Schema(
        description = "List of objects holding the key and the name of the resolved campaigns to included in the report and obtained based on campaign name pattern",
        required = false
    )
    val resolvedCampaigns: List<CampaignKeyAndName> = emptyList(),

    @field:Schema(
        description = "List of scenario names patterns to be included in the report",
        required = false
    )
    val scenarioNamesPatterns: List<String> = emptyList(),

    @field:Schema(
        description = "List of scenario names to be included in the report and obtained based on scenario name pattern",
        required = false
    )
    val resolvedScenarioNames: List<String> = emptyList(),

    @field:Schema(
        description = "List of data component to include in the report",
        required = false
    )
    val dataComponents: List<@Valid DataComponent> = emptyList()
)