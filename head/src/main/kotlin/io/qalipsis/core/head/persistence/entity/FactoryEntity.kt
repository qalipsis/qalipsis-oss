package io.qalipsis.core.head.persistence.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import java.time.Instant

/**
 * Entity to encapsulate data of factory table
 *
 * @author rklymenko
 */
@MappedEntity("factory", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
data class FactoryEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Version
    val version: Instant,
    val nodeId: String,
    val registrationTimestamp: Instant,
    val registrationNodeId: String,
    @field:Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "factoryId")
    val selectors: List<FactorySelectorEntity>
) : Entity {

    constructor(
        nodeId: String,
        registrationTimestamp: Instant,
        registrationNodeId: String,
        selectors: List<FactorySelectorEntity> = emptyList()
    ) : this(-1, Instant.EPOCH, nodeId, registrationTimestamp, registrationNodeId, selectors)
}