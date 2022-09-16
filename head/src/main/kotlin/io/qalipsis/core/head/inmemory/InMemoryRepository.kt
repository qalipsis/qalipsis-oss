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

package io.qalipsis.core.head.inmemory

import io.qalipsis.core.persistence.InMemoryEntity

/**
 * Interface to store and provide persistent entities.
 *
 * @author Eric Jessé
 */
interface InMemoryRepository<ENTITY : InMemoryEntity<ID>, ID : Any> {

    fun save(entity: ENTITY): ENTITY

    fun saveAll(entities: Collection<ENTITY>): Collection<ENTITY>

    fun getAll(): Collection<ENTITY>

    fun get(id: ID): ENTITY?

    fun getAll(ids: Collection<ID>): Collection<ENTITY>

    fun delete(id: ID): ENTITY?

}
