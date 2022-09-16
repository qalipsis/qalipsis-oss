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
import io.micronaut.data.annotation.Relation.Cascade
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
    @field:Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "factoryId", cascade = [Cascade.ALL])
    val tags: List<FactoryTagEntity>,
    val zone: String? = null
) : Entity {

    constructor(
        tenantId: Long = -1,
        nodeId: String,
        registrationTimestamp: Instant,
        registrationNodeId: String,
        unicastChannel: String,
        tags: List<FactoryTagEntity> = emptyList(),
        zone: String? = null
    ) : this(
        -1,
        Instant.EPOCH,
        tenantId,
        nodeId,
        registrationTimestamp,
        registrationNodeId,
        unicastChannel,
        tags,
        zone
    )

    fun toModel(activeScenarios: Collection<String> = emptySet()): Factory {
        return Factory(
            nodeId = nodeId,
            registrationTimestamp = registrationTimestamp,
            unicastChannel = unicastChannel,
            version = version,
            tags = tags.associate { it.key to it.value },
            activeScenarios = activeScenarios,
            zone = zone
        )
    }
}