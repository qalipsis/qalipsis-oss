/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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
import io.qalipsis.core.head.model.Zone
import java.net.URL
import java.time.Instant
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

/**
 * Details of a campaign.
 *
 * @property id internal database ID
 * @property version version of the entity
 * @property creation instant time of creation
 * @property key unique public identifier of the zone
 * @property title a complete display name of the zone, usually the country
 * @property description detailed description of the zone, generally the region, datacenter and the localization details
 * @property imagePath url to display for the zone
 * @property enabled boolean flag to specify if the zone is enabled for use or not
 *
 * @author Francisca Eze
 */
@MappedEntity("zone", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
data class ZoneEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Version
    val version: Instant,
    val creation: Instant = Instant.now(),
    @field:NotBlank
    @field:Size(min = 2, max = 3)
    val key: String,
    @field:Size(min = 3, max = 20)
    val title: String,
    @field:Size(min = 3, max = 50)
    val description: String? = null,
    @field:Size(min = 3, max = 50)
    val imagePath: URL? = null,
    val enabled: Boolean = true,

) : Entity {

    constructor(
        creation: Instant = Instant.now(),
        key: String,
        title: String,
        description: String?,
        imagePath: URL?,
        enabled: Boolean = true
    ) : this(
        id = -1,
        creation = creation,
        version = Instant.now(),
        key = key,
        title = title,
        description = description,
        imagePath = imagePath,
        enabled = enabled
    )

    internal fun toModel(): Zone {
        return Zone(
            key = key,
            title = title,
            description = description,
            imagePath = imagePath,
            enabled = enabled
        )
    }
}