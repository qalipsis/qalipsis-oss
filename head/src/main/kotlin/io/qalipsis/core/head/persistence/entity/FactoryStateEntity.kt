package io.qalipsis.core.head.persistence.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import java.time.Instant

/**
 * Entity to encapsulate data of factory_state table
 *
 * @author rklymenko
 */
@MappedEntity("factory_state", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
data class FactoryStateEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Version
    override val version: Instant,
    val factoryId: Long,
    val healthTimestamp: Instant,
    val state: FactoryStateValue
) : VersionedEntity {

    constructor(
        factoryId: Long,
        healthTimestamp: Instant,
        factoryState: FactoryStateValue,
        version: Instant = Instant.now()
    ) : this(
        -1,
        version,
        factoryId,
        healthTimestamp,
        factoryState
    )
}