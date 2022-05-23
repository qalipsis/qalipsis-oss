package io.qalipsis.core.head.jdbc.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.model.naming.NamingStrategies
import io.qalipsis.core.head.jdbc.SelectorEntity
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

/**
 * Entity to encapsulate data of factory_selector table
 *
 * @author rklymenko
 */
@MappedEntity("factory_tag", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
internal data class FactoryTagEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    val factoryId: Long,
    @field:NotBlank
    @field:Size(min = 1, max = 50)
    override val key: String,
    @field:NotBlank
    @field:Size(min = 1, max = 50)
    override val value: String,
) : Entity, SelectorEntity<FactoryTagEntity> {

    constructor(
        factoryId: Long,
        selectorKey: String,
        selectorValue: String
    ) : this(-1, factoryId, selectorKey, selectorValue)

    override fun withValue(value: String): FactoryTagEntity {
        return this.copy(value = value)
    }
}