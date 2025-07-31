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
import io.qalipsis.core.head.security.User
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Profile details of a QALIPSIS user.
 *
 * @author Eric Jessé
 */
@Introspected
@Schema(
    name = "Profile",
    title = "Profile of a QALIPSIS user",
    description = "Details of a QALIPSIS user and its profile"
)
open class Profile<U : User>(
    @field:Schema(description = "Details of the user")
    val user: U,

    @field:Schema(description = "Tenants accessible to the user")
    val tenants: Collection<Tenant>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Profile<*>

        if (user != other.user) return false
        if (tenants != other.tenants) return false

        return true
    }

    override fun hashCode(): Int {
        var result = user.hashCode()
        result = 31 * result + tenants.hashCode()
        return result
    }

    override fun toString(): String {
        return "Profile(user=$user, tenants=$tenants)"
    }

}
