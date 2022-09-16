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
import java.util.concurrent.ConcurrentHashMap

/**
 * Component to store and provides entities with in-memory storage.
 *
 * @author Eric Jessé
 */
internal abstract class AbstractInMemoryRepository<ENTITY : InMemoryEntity<ID>, ID : Any> :
    InMemoryRepository<ENTITY, ID> {

    private val entities = ConcurrentHashMap<ID, ENTITY>()

    override fun save(entity: ENTITY): ENTITY {
        entities[entity.name] = entity
        return entity
    }

    override fun saveAll(entities: Collection<ENTITY>): Collection<ENTITY> {
        entities.forEach { save(it) }
        return entities
    }

    override fun getAll(): Collection<ENTITY> {
        val result = mutableListOf<ENTITY>()
        result.addAll(entities.values)
        return result
    }

    override fun get(id: ID): ENTITY? {
        return entities[id]
    }

    override fun getAll(ids: Collection<ID>): Collection<ENTITY> {
        return ids.mapNotNull { id -> get(id) }
    }

    override fun delete(id: ID): ENTITY? {
        return entities.remove(id)
    }

}
