package io.qalipsis.core.head.jdbc.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import io.qalipsis.core.head.model.Factory
import java.time.Instant
import javax.validation.constraints.NotBlank

/**
 * Entity to encapsulate data of factory table.
 *
 * @author rklymenko
 */
@MappedEntity("factory", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
internal data class FactoryEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Version
    val version: Instant,
    val tenantId: Long,
    val nodeId: String,
    val registrationTimestamp: Instant,
    @field:NotBlank
    val registrationNodeId: String,
    @field:NotBlank
    val unicastChannel: String,
    @field:Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "factoryId")
    val tags: List<FactorySelectorEntity>
) : Entity {

    constructor(
        tenantId: Long = -1,
        nodeId: String,
        registrationTimestamp: Instant,
        registrationNodeId: String,
        unicastChannel: String,
        selectors: List<FactorySelectorEntity> = emptyList()
    ) : this(
        -1,
        Instant.EPOCH,
        tenantId,
        nodeId,
        registrationTimestamp,
        registrationNodeId,
        unicastChannel,
        selectors
    )

    fun toModel(activeScenarios: Collection<String> = emptySet()): Factory {
        return Factory(
            nodeId = nodeId,
            registrationTimestamp = registrationTimestamp,
            unicastChannel = unicastChannel,
            version = version,
            selectors = tags.associate { it.key to it.value },
            activeScenarios = activeScenarios
        )
    }
}