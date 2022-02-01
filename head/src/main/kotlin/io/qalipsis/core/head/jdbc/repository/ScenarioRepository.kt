package io.qalipsis.core.head.jdbc.repository

import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.head.jdbc.entity.ScenarioEntity

/**
 * Micronaut data async repository to operate with scenario entity
 *
 * @author rklymenko
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
internal interface ScenarioRepository : CoroutineCrudRepository<ScenarioEntity, Long> {

    @Query("UPDATE scenario SET enabled = FALSE WHERE id = :id")
    override suspend fun deleteById(id: Long): Int

    @Query("UPDATE scenario SET enabled = FALSE WHERE id = :id")
    override suspend fun delete(entity: ScenarioEntity): Int

    @Query("UPDATE scenario SET enabled = FALSE WHERE id IN (:id)")
    override suspend fun deleteAll(entities: Iterable<ScenarioEntity>): Int

    @Query("SELECT * FROM scenario WHERE name in (:names) AND enabled = true")
    @Join(value = "dags", type = Join.Type.LEFT)
    suspend fun findActiveByName(names: Collection<String>): List<ScenarioEntity>

    @Join(value = "dags", type = Join.Type.LEFT)
    suspend fun findByFactoryId(factoryId: Long): List<ScenarioEntity>

}