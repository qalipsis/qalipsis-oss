package io.qalipsis.core.head.model

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessageSeverity
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.PositiveOrZero

/**
 * Model of campaign report.

 * @author Palina Bril
 */
@Introspected
@Schema(
    name = "Report of campaign execution",
    title = "Details of the execution of a completed or running campaign and its scenario"
)
internal data class CampaignReport(
    @field:Schema(description = "Unique identifier of the campaign")
    @field:NotBlank
    val campaignKey: CampaignKey,

    @field:Schema(description = "Date and time when the campaign started")
    val start: Instant,

    @field:Schema(
        description = "Date and time when the campaign was completed, whether successfully or not",
        required = false
    )
    val end: Instant?,

    @field:Schema(description = "Counts of minions when the campaign started")
    @field:PositiveOrZero
    val startedMinions: Int,

    @field:Schema(description = "Counts of minions that completed the campaign")
    @field:PositiveOrZero
    val completedMinions: Int,

    @field:Schema(description = "Counts of minions that successfully completed the campaign")
    @field:PositiveOrZero
    val successfulExecutions: Int,

    @field:Schema(description = "Counts of minions that failed to execute the campaign")
    @field:PositiveOrZero
    val failedExecutions: Int,

    @field:Schema(description = "Overall execution status of the campaign")
    val status: ExecutionStatus,

    @field:Schema(description = "The list of the scenario reports for the campaign")
    @field:Valid
    val scenariosReports: Collection<@Valid ScenarioReport>
)

/**
 * Model of scenario report.
 *
 * @author Palina Bril
 */
@Introspected
@Schema(
    name = "Details about execution report of a completed scenario",
    title = "Details for the scenario report to retrieve from the REST endpoint"
)
internal data class ScenarioReport(
    @field:Schema(description = "Unique identifier of the campaign")
    @field:NotBlank
    val campaignKey: CampaignKey,

    @field:Schema(description = "Identifier of the scenario")
    @field:NotBlank
    val scenarioName: ScenarioName,

    @field:Schema(description = "Date and time when the scenario started")
    val start: Instant,

    @field:Schema(description = "Date and time when the scenario was completed, whether successfully or not")
    val end: Instant,

    @field:Schema(description = "Counts of minions when the scenario started")
    @field:PositiveOrZero
    val startedMinions: Int,

    @field:Schema(description = "Counts of minions that completed their scenario")
    @field:PositiveOrZero
    val completedMinions: Int,

    @field:Schema(description = "Counts of minions that successfully completed their scenario")
    @field:PositiveOrZero
    val successfulExecutions: Int,

    @field:Schema(description = "Counts of minions that failed to execute their scenario")
    @field:PositiveOrZero
    val failedExecutions: Int,

    @field:Schema(description = "Overall execution status of the scenario")
    val status: ExecutionStatus,

    @field:Schema(description = "The list of the report messages for the scenario")
    @field:Valid
    val messages: List<@Valid ReportMessage>
)

/**
 * Model of report message.
 *
 * @author Palina Bril
 */
@Introspected
@Schema(
    name = "Details about report message of a completed scenario",
    title = "Details for the scenario report message to retrieve from the REST endpoint"
)
internal data class ReportMessage(
    @field:Schema(description = "Identifier of the step")
    @field:NotBlank
    val stepName: StepName,

    @field:Schema(description = "Identifier of the message")
    @field:NotBlank
    val messageId: Any,

    @field:Schema(description = "Severity of the report message")
    val severity: ReportMessageSeverity,

    @field:Schema(description = "The message itself")
    val message: String
)