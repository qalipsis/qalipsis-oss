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

package io.qalipsis.api.states

import io.qalipsis.api.context.MinionId

/**
 * Interface to share mutable states between steps. The implementations can be backed by in-memory or persistent caches.
 *
 * @author Eric Jessé
 */
interface SharedStateRegistry {

    suspend fun set(definition: SharedStateDefinition, payload: Any?)

    suspend fun set(values: Map<SharedStateDefinition, Any?>)

    suspend fun <T> get(definition: SharedStateDefinition): T?

    suspend fun <T> remove(definition: SharedStateDefinition): T?

    suspend fun get(definitions: Iterable<SharedStateDefinition>): Map<String, Any?>

    suspend fun remove(definitions: Iterable<SharedStateDefinition>): Map<String, Any?>

    suspend fun contains(definition: SharedStateDefinition): Boolean

    suspend fun clear()

    suspend fun clear(minionIds: Collection<MinionId>)

}
