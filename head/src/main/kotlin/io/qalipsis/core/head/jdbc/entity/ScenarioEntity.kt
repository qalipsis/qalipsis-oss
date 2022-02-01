package io.qalipsis.core.head.jdbc.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import io.qalipsis.core.campaigns.ScenarioSummary
import java.time.Instant
import javax.validation.constraints.Max
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Positive
import javax.validation.constraints.Size

/**
 * Entity to encapsulate data of scenario table.
 *
 * @author rklymenko
 */
@MappedEntity("scenario", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
internal data class ScenarioEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Version
    override val version: Instant,
    val factoryId: Long,
    @field:NotBlank
    @field:Size(min = 2, max = 255)
    val name: String,
    @field:Positive
    @field:Max(1000000)
    val defaultMinionsCount: Int,
    @field:Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "scenarioId")
    val dags: List<DirectedAcyclicGraphEntity>,
    val enabled: Boolean
) : VersionedEntity {

    constructor(
        factoryId: Long,
        scenarioName: String,
        defaultMinionsCount: Int,
        dags: List<DirectedAcyclicGraphEntity> = emptyList(),
        enabled: Boolean = true
    ) : this(-1, Instant.now(), factoryId, scenarioName, defaultMinionsCount, dags, enabled)

    fun toModel(): ScenarioSummary {
        return ScenarioSummary(
            id = name,
            minionsCount = defaultMinionsCount,
            directedAcyclicGraphs = dags.map(DirectedAcyclicGraphEntity::toModel)
        )
    }
}