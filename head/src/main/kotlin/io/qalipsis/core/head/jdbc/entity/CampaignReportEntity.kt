package io.qalipsis.core.head.jdbc.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import java.time.Instant

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
    val campaignId: Long,
    val startedMinions: Int,
    val completedMinions: Int,
    val successfulExecutions: Int,
    val failedExecutions: Int,
    @field:Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "campaignReportId")
    var scenariosReports: List<ScenarioReportEntity>?
) : Entity {

    constructor(
        campaignId: Long,
        startedMinions: Int = 0,
        completedMinions: Int = 0,
        successfulExecutions: Int = 0,
        failedExecutions: Int = 0,
        scenariosReports: List<ScenarioReportEntity>? = null
    ) : this(
        -1,
        Instant.EPOCH,
        campaignId, startedMinions, completedMinions, successfulExecutions, failedExecutions, scenariosReports
    )
}