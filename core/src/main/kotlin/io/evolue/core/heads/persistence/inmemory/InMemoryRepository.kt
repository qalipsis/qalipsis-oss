package io.evolue.core.heads.persistence.inmemory

import io.evolue.core.heads.persistence.Entity
import io.evolue.core.heads.persistence.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * Component to store and provides entities with in-memory storage.
 *
 * @author Eric Jessé
 */
internal abstract class InMemoryRepository<ENTITY : Entity<ID>, ID : Any> : Repository<ENTITY, ID> {

    private val entities = ConcurrentHashMap<ID, ENTITY>()

    override fun save(entity: ENTITY): ENTITY {
        entities[entity.id] = entity
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
        return ids.map { id -> get(id) }.filterNotNull().toList()
    }

    override fun delete(id: ID): ENTITY? {
        return entities.remove(id)
    }

}
