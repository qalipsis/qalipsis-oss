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

package io.qalipsis.cluster.security

/**
 * Role of a user into QALIPSIS.
 *
 * @property publicName name publicly available.
 * @property permissions permissions granted to the users having the role.
 *
 */
enum class RoleName(
    val publicName: String,
    val permissions: Collection<Permission> = emptySet()
) {
    /**
     * Role of user having the administrator rights.
     */
    SUPER_ADMINISTRATOR("super-admin", Permissions.ALL_PERMISSIONS),

    /**
     * Role of user having the rights to edit the tenant details and its users.
     */
    TENANT_ADMINISTRATOR("tenant-admin", Permissions.FOR_TENANT_ADMINISTRATOR),

    /**
     * Role of user having the rights to start and edit campaigns.
     */
    TESTER("tester", Permissions.FOR_TESTER),

    /**
     * Role of user having the rights to vizualize the results of campaigns.
     */
    REPORTER("reporter", Permissions.FOR_REPORTER);

    companion object {

        private val ROLES_BY_NAMES = values().associateBy { it.publicName }

        fun fromPublicName(publicName: String) = ROLES_BY_NAMES[publicName]!!

    }
}