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