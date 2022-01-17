package io.qalipsis.core.head.persistence.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import java.time.Instant

/**
 * Entity to encapsulate data of scenario table.
 *
 * @author rklymenko
 */
@MappedEntity("scenario", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
data class ScenarioEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Version
    override val version: Instant,
    val factoryId: Long,
    val name: String,
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
}