package io.qalipsis.core.head.jdbc.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import io.qalipsis.api.report.ReportMessageSeverity
import java.time.Instant
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

/**
 * Entity to encapsulate data of scenario report message table.
 *
 * @author Palina Bril
 */
@MappedEntity("scenario_report_message", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
internal data class ScenarioReportMessageEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Version
    override val version: Instant,
    val scenarioReportId: Long,
    val stepId: String,
    val messageId: String,
    val severity: ReportMessageSeverity,
    @field:NotBlank
    @field:Size(min = 1, max = 255)
    val message: String
) : VersionedEntity {

}