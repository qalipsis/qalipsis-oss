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

package io.qalipsis.core.head.model

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/**
 * Tenant, representing an organization in QALIPSIS.
 */
@Introspected
@Schema(name = "Tenant", title = "A Tenant represents an organization in QALIPSIS")
data class Tenant(
    @field:Schema(description = "Unique identifier of the tenant")
    val reference: String,

    @field:Schema(description = "Name of the tenant for display")
    val displayName: String,

    @field:Schema(description = "Last update of the entity")
    val version: Instant
)