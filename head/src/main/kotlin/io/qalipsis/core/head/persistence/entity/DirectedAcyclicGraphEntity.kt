package io.qalipsis.core.head.persistence.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import java.time.Instant
import javax.validation.constraints.NotBlank

/**
 * Entity to encapsulate data of directed_acyclic_graph table
 *
 * @author rklymenko
 */
@MappedEntity("directed_acyclic_graph", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
data class DirectedAcyclicGraphEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Version
    override val version: Instant,
    val scenarioId: Long,
    @field:NotBlank
    val name: String,
    val singleton: Boolean,
    val underLoad: Boolean,
    val numberOfSteps: Int,
    @field:Relation(
        value = Relation.Kind.ONE_TO_MANY,
        mappedBy = "directedAcyclicGraphId",
        cascade = [Relation.Cascade.ALL]
    )
    val selectors: List<DirectedAcyclicGraphSelectorEntity>
) : VersionedEntity {

    constructor(
        scenarioId: Long,
        name: String,
        singleton: Boolean,
        underLoad: Boolean,
        numberOfSteps: Int,
        selectors: List<DirectedAcyclicGraphSelectorEntity> = emptyList(),
        version: Instant = Instant.now()
    ) : this(-1, version, scenarioId, name, singleton, underLoad, numberOfSteps, selectors)
}