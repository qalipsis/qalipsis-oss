package io.evolue.core.heads.persistence.inmemory

import io.evolue.core.heads.persistence.Entity
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
internal class InMemoryRepositoryTest {

    @Test
    internal fun `save then get and delete`() {
        val entity = TestEntity(123)
        val repository = TestEntityRepository()

        var saved = repository.save(entity)
        Assertions.assertSame(entity, saved)

        val fetched = repository.get(123)
        Assertions.assertSame(entity, fetched)

        val newEntityWithSameId = TestEntity(123)
        saved = repository.save(newEntityWithSameId)
        Assertions.assertSame(newEntityWithSameId, saved)

        repository.delete(123)
        Assertions.assertNull(repository.get(123))
    }

    @Test
    internal fun `save all then get by ids and get all`() {
        val entity1 = TestEntity(123)
        val entity2 = TestEntity(456)
        val entity3 = TestEntity(789)
        val repository = TestEntityRepository()

        val entities = listOf(entity1, entity2, entity3)
        var result = repository.saveAll(entities)
        Assertions.assertEquals(3, result.size)
        Assertions.assertSame(entities, result)

        result = repository.getAll(listOf(123, 789)) as List<TestEntity>
        Assertions.assertEquals(2, result.size)
        Assertions.assertSame(entity1, result[0])
        Assertions.assertSame(entity3, result[1])

        result = repository.getAll().toSet()
        Assertions.assertEquals(3, result.size)
        Assertions.assertEquals(entities.toSet(), result)
    }

    data class TestEntity(
        override var id: Long
    ) : Entity<Long>

    class TestEntityRepository : InMemoryRepository<Entity<Long>, Long>()
}
