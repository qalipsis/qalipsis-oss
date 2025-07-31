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

package io.qalipsis.core.head.security

import io.micronaut.context.annotation.Secondary
import io.qalipsis.core.head.jdbc.entity.Defaults
import jakarta.inject.Singleton

/**
 * Mock implementation of [TenantProvider] where only the default tenant is managed.
 */
@Singleton
@Secondary
class TenantProviderImpl : TenantProvider {

    override suspend fun findIdByReference(reference: String): Long = 1

    override suspend fun findReferenceById(id: Long): String = Defaults.TENANT
}