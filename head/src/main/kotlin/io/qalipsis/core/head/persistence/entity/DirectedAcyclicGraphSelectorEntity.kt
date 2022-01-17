package io.qalipsis.core.head.persistence.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.model.naming.NamingStrategies
import io.qalipsis.core.head.persistence.SelectorEntity

/**
 * Entity to encapsulate data of directed_acyclic_graph_selector table
 *
 * @author rklymenko
 */
@MappedEntity("directed_acyclic_graph_selector", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
data class DirectedAcyclicGraphSelectorEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    val directedAcyclicGraphId: Long,
    override val key: String,
    override val value: String
) : Entity, SelectorEntity<DirectedAcyclicGraphSelectorEntity> {

    constructor(
        dagId: Long,
        selectorKey: String,
        selectorValue: String
    ) : this(-1, dagId, selectorKey, selectorValue)

    override fun withValue(value: String): DirectedAcyclicGraphSelectorEntity {
        return this.copy(value = value)
    }
}
