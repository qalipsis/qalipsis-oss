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

package io.qalipsis.core.head.zone

import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.model.naming.NamingStrategies

/**
 * Details of a campaign.
 *
 * @property id internal database ID
 *
 * @author Francisca Eze
 */
@MappedEntity("zone_tenant", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
data class ZoneTenantEntity(
    @EmbeddedId
    val id: ZoneTenantId
)

/**
 * @property zoneId unique zone identifier
 * @property tenantId unique tenant identifier
 */
@Embeddable
data class ZoneTenantId(
    @MappedProperty("zone_id")
    val zoneId: Long,
    @MappedProperty("tenant_id")
    val tenantId: Long
)