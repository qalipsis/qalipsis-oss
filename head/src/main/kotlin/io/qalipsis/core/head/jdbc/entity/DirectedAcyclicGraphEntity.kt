/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

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
data class DirectedAcyclicGraphEntity(
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
    val tags: List<DirectedAcyclicGraphTagEntity>
) : VersionedEntity {

    constructor(
        scenarioId: Long,
        name: String,
        root: Boolean,
        singleton: Boolean,
        underLoad: Boolean,
        numberOfSteps: Int,
        tags: List<DirectedAcyclicGraphTagEntity> = emptyList(),
        version: Instant = Instant.now()
    ) : this(-1, version, scenarioId, name, root, singleton, underLoad, numberOfSteps, tags)

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