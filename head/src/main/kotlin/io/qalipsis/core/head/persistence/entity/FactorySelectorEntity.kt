package io.qalipsis.core.head.persistence.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.model.naming.NamingStrategies
import io.qalipsis.core.head.persistence.SelectorEntity

/**
 * Entity to encapsulate data of factory_selector table
 *
 * @author rklymenko
 */
@MappedEntity("factory_selector", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
data class FactorySelectorEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    val factoryId: Long,
    override val key: String,
    override val value: String,
) : Entity, SelectorEntity<FactorySelectorEntity> {

    constructor(
        factoryId: Long,
        selectorKey: String,
        selectorValue: String
    ) : this(-1, factoryId, selectorKey, selectorValue)

    override fun withValue(value: String): FactorySelectorEntity {
        return this.copy(value = value)
    }
}