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

package io.qalipsis.core.head.security

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Details of a QALIPSIS user.
 *
 * @author Palina Bril
 */
@Schema(name = "User", title = "User of QALIPSIS", description = "Details of a QALIPSIS user")
internal data class User(
    @field:Schema(description = "Tenant owning the user", required = false)
    val tenant: String,

    @field:Schema(description = "Unique identifier of the user")
    val username: String

)