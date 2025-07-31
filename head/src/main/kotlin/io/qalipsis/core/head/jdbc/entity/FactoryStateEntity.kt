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
    val latency: Long,
    val state: FactoryStateValue
) : VersionedEntity {

    constructor(
        version: Instant = Instant.now(),
        factoryId: Long,
        healthTimestamp: Instant,
        latency: Long,
        state: FactoryStateValue
    ) : this(
        -1,
        version,
        factoryId,
        healthTimestamp,
        latency,
        state
    )
}