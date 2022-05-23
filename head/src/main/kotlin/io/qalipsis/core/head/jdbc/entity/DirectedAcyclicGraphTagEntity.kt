package io.qalipsis.core.head.jdbc.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.model.naming.NamingStrategies
import io.qalipsis.core.head.jdbc.SelectorEntity
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

/**
 * Entity to encapsulate data of directed_acyclic_graph_selector table
 *
 * @author rklymenko
 */
@MappedEntity("directed_acyclic_graph_tag", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
internal data class DirectedAcyclicGraphTagEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    val directedAcyclicGraphId: Long,
    @field:NotBlank
    @field:Size(min = 1, max = 50)
    override val key: String,
    @field:NotBlank
    @field:Size(min = 1, max = 50)
    override val value: String
) : Entity, SelectorEntity<DirectedAcyclicGraphTagEntity> {

    constructor(
        dagId: Long,
        selectorKey: String,
        selectorValue: String
    ) : this(-1, dagId, selectorKey, selectorValue)

    override fun withValue(value: String): DirectedAcyclicGraphTagEntity {
        return this.copy(value = value)
    }
}
