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

import io.qalipsis.core.head.model.Profile
import io.qalipsis.core.head.model.Tenant
import io.qalipsis.core.head.security.QalipsisUser
import java.time.Instant

/**
 * Constants for the default values.
 *
 * @author Eric Jessé
 */
object Defaults {

    /**
     * Username of QALIPSIS default user.
     */
    const val USERNAME = "_qalipsis_"

    /**
     * Reference of QALIPSIS default tenant.
     */
    const val TENANT = "_qalipsis_ten_"

    /**
     * Profile of QALIPSIS default user.
     */
    val PROFILE = Profile(
        user = QalipsisUser(tenant = TENANT, username = USERNAME),
        tenants = setOf(Tenant(TENANT, "", Instant.now()))
    )
}