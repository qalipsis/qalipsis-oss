package io.qalipsis.core.head.jdbc.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import java.time.Instant
import javax.validation.constraints.Positive

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
    @field:Positive
    val startedMinions: Int,
    val completedMinions: Int,
    val successfulExecutions: Int,
    val failedExecutions: Int,
    @field:Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "campaignReportId")
    val scenariosReports: List<ScenarioReportEntity>
) : Entity {

}