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
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
internal class AbstractInMemoryRepositoryTest {

    @Test
    internal fun `save then get and delete`() {
        val entity = TestInMemoryEntity(123)
        val repository = TestEntityRepository()

        var saved = repository.save(entity)
        Assertions.assertSame(entity, saved)

        val fetched = repository.get(123)
        Assertions.assertSame(entity, fetched)

        val newEntityWithSameId = TestInMemoryEntity(123)
        saved = repository.save(newEntityWithSameId)
        Assertions.assertSame(newEntityWithSameId, saved)

        repository.delete(123)
        Assertions.assertNull(repository.get(123))
    }

    @Test
    internal fun `save all then get by ids and get all`() {
        val entity1 = TestInMemoryEntity(123)
        val entity2 = TestInMemoryEntity(456)
        val entity3 = TestInMemoryEntity(789)
        val repository = TestEntityRepository()

        val entities = listOf(entity1, entity2, entity3)
        var result = repository.saveAll(entities)
        Assertions.assertEquals(3, result.size)
        Assertions.assertSame(entities, result)

        result = repository.getAll(listOf(123, 789)) as List<TestInMemoryEntity>
        Assertions.assertEquals(2, result.size)
        Assertions.assertSame(entity1, result[0])
        Assertions.assertSame(entity3, result[1])

        result = repository.getAll().toSet()
        Assertions.assertEquals(3, result.size)
        Assertions.assertEquals(entities.toSet(), result)
    }

    data class TestInMemoryEntity(
        override var name: Long
    ) : InMemoryEntity<Long>

    class TestEntityRepository : AbstractInMemoryRepository<InMemoryEntity<Long>, Long>()
}
