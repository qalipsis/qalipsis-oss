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
