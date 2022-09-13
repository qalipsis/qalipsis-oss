package io.qalipsis.core.head.jdbc.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import io.qalipsis.api.report.ExecutionStatus
import java.time.Instant
import javax.validation.constraints.NotNull
import javax.validation.constraints.PositiveOrZero

/**
 * Details of a campaign report.
 *
 * @author Palina Bril
 */
@MappedEntity("campaign_report", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
internal data class CampaignReportEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Version
    val version: Instant,
    @field:NotNull
    val campaignId: Long,
    @field:PositiveOrZero
    val startedMinions: Int,
    @field:PositiveOrZero
    val completedMinions: Int,
    @field:PositiveOrZero
    val successfulExecutions: Int,
    @field:PositiveOrZero
    val failedExecutions: Int,
    val status: ExecutionStatus,
    @field:Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "campaignReportId")
    val scenariosReports: List<ScenarioReportEntity>
) : Entity {

    constructor(
        campaignId: Long,
        startedMinions: Int = 0,
        completedMinions: Int = 0,
        successfulExecutions: Int = 0,
        failedExecutions: Int = 0,
        status: ExecutionStatus,
        scenariosReports: List<ScenarioReportEntity> = emptyList()
    ) : this(
        -1,
        Instant.EPOCH,
        campaignId, startedMinions, completedMinions, successfulExecutions, failedExecutions, status, scenariosReports
    )
}