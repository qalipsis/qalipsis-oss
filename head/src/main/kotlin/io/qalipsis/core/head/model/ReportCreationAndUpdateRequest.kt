package io.qalipsis.core.head.model

import io.micronaut.core.annotation.Introspected
import io.qalipsis.core.head.report.SharingMode
import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

/**
 * @author Joël Valère
 */
@Introspected
@Schema(
    name = "Request to create or update a report",
    title = "Details for the creation or the update of a new report into QALIPSIS"
)
internal data class ReportCreationAndUpdateRequest(

    @field:Schema(description = "Display name of the report, should be unique into a tenant", required = true)
    @field:NotBlank
    @field:Size(min = 1, max = 200)
    val displayName: String,

    @field:Schema(description = "Sharing mode with the other members of the tenant", required = false)
    val sharingMode: SharingMode = SharingMode.READONLY,

    @field:Schema(
        description = "List of keys that represent campaigns to include in the report",
        required = false
    )
    val campaignKeys: List<String> = emptyList(),

    @field:Schema(
        description = "List of keys that represent campaigns to include in the report",
        required = false
    )
    val campaignNamesPatterns: List<String> = emptyList(),

    @field:Schema(
        description = "List of scenarios to include in the report",
        required = false
    )
    val scenarioNamesPatterns: List<String> = emptyList(),

    @field:Schema(
        description = "List of data component to include in the report",
        required = false
    )
    val dataComponents: List<@Valid DataComponentCreationAndUpdateRequest> = emptyList(),
)

