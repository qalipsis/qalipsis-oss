package io.qalipsis.core.head.jdbc.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import io.qalipsis.api.report.ExecutionStatus
import java.time.Instant

/**
 * Entity to encapsulate data of scenario report table.
 *
 * @author Palina Bril
 */
@MappedEntity("scenario_report", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
internal data class ScenarioReportEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Version
    override val version: Instant,
    val campaignReportId: Long,
    val start: Instant,
    val end: Instant,
    val startedMinions: Int,
    val completedMinions: Int,
    val successfulExecutions: Int,
    val failedExecutions: Int,
    val status: ExecutionStatus,
    @field:Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "scenarioReportId")
    val messages: List<ScenarioReportMessageEntity>
) : VersionedEntity {

    constructor(
        campaignReportId: Long,
        start: Instant = Instant.now(),
        end: Instant = Instant.now(),
        startedMinions: Int = 0,
        completedMinions: Int = 0,
        successfulExecutions: Int = 0,
        failedExecutions: Int = 0,
        status: ExecutionStatus,
        messages: List<ScenarioReportMessageEntity> = mutableListOf()
    ) : this(
        -1,
        Instant.EPOCH,
        campaignReportId,
        start,
        end,
        startedMinions,
        completedMinions,
        successfulExecutions,
        failedExecutions,
        status,
        messages
    )
}