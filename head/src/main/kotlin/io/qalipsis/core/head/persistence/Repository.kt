package io.qalipsis.core.head.persistence

import io.qalipsis.core.persistence.Entity

/**
 * Interface to store and provide persistent entities.
 *
 * @author Eric Jessé
 */
interface Repository<ENTITY : Entity<ID>, ID : Any> {

    fun save(entity: ENTITY): ENTITY

    fun saveAll(entities: Collection<ENTITY>): Collection<ENTITY>

    fun getAll(): Collection<ENTITY>

    fun get(id: ID): ENTITY?

    fun getAll(ids: Collection<ID>): Collection<ENTITY>

    fun delete(id: ID): ENTITY?

}
