package io.qalipsis.core.head.jdbc.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import io.qalipsis.core.campaigns.DirectedAcyclicGraphSummary
import java.time.Instant
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

/**
 * Entity to encapsulate data of directed_acyclic_graph table
 *
 * @author rklymenko
 */
@MappedEntity("directed_acyclic_graph", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
internal data class DirectedAcyclicGraphEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Version
    override val version: Instant,
    val scenarioId: Long,
    @field:NotBlank
    @field:Size(min = 1, max = 255)
    val name: String,
    val root: Boolean,
    val singleton: Boolean,
    val underLoad: Boolean,
    val numberOfSteps: Int,
    @field:Relation(
        value = Relation.Kind.ONE_TO_MANY,
        mappedBy = "directedAcyclicGraphId",
        cascade = [Relation.Cascade.ALL]
    )
    val tags: List<DirectedAcyclicGraphSelectorEntity>
) : VersionedEntity {

    constructor(
        scenarioId: Long,
        name: String,
        isRoot: Boolean,
        singleton: Boolean,
        underLoad: Boolean,
        numberOfSteps: Int,
        selectors: List<DirectedAcyclicGraphSelectorEntity> = emptyList(),
        version: Instant = Instant.now()
    ) : this(-1, version, scenarioId, name, isRoot, singleton, underLoad, numberOfSteps, selectors)

    fun toModel(): DirectedAcyclicGraphSummary {
        return DirectedAcyclicGraphSummary(
            name = name,
            isSingleton = singleton,
            isRoot = root,
            isUnderLoad = underLoad,
            numberOfSteps = numberOfSteps,
            tags = tags.associate { it.key to it.value },
        )
    }
}