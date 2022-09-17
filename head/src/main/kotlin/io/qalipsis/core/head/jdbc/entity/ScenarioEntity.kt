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
import io.qalipsis.core.campaigns.ScenarioSummary
import java.time.Instant
import javax.validation.constraints.Max
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Positive
import javax.validation.constraints.Size

/**
 * Entity to encapsulate data of scenario table.
 *
 * @author rklymenko
 */
@MappedEntity("scenario", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
internal data class ScenarioEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Version
    override val version: Instant,
    val factoryId: Long,
    @field:NotBlank
    @field:Size(min = 2, max = 255)
    val name: String,
    @field:Positive
    @field:Max(1000000)
    val defaultMinionsCount: Int,
    @field:Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "scenarioId")
    val dags: List<DirectedAcyclicGraphEntity>,
    val enabled: Boolean
) : VersionedEntity {

    constructor(
        factoryId: Long,
        scenarioName: String,
        defaultMinionsCount: Int,
        dags: List<DirectedAcyclicGraphEntity> = emptyList(),
        enabled: Boolean = true
    ) : this(-1, Instant.now(), factoryId, scenarioName, defaultMinionsCount, dags, enabled)

    fun toModel(): ScenarioSummary {
        return ScenarioSummary(
            name = name,
            minionsCount = defaultMinionsCount,
            directedAcyclicGraphs = dags.map(DirectedAcyclicGraphEntity::toModel)
        )
    }
}