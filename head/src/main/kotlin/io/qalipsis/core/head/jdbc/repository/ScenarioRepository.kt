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

    @Query(
        "SELECT * FROM scenario LEFT JOIN factory ON factory_id = factory.id WHERE name in (:names) AND enabled = true AND EXISTS (SELECT * FROM tenant WHERE reference = :tenant AND id = factory.tenant_id)"
    )
    @Join(value = "dags", type = Join.Type.LEFT)
    suspend fun findActiveByName(tenant: String, names: Collection<String>): List<ScenarioEntity>

    @Query(
        "SELECT * FROM scenario LEFT JOIN factory ON factory_id = factory.id WHERE factory_id = :factoryId AND EXISTS (SELECT * FROM tenant WHERE reference = :tenant AND id = factory.tenant_id)"
    )
    @Join(value = "dags", type = Join.Type.LEFT)
    suspend fun findByFactoryId(tenant: String, factoryId: Long): List<ScenarioEntity>

    @Query(
        """SELECT * FROM scenario LEFT JOIN factory ON factory_id = factory.id 
            WHERE enabled = true AND EXISTS (SELECT * FROM tenant WHERE reference = :tenant AND id = factory.tenant_id) 
            ORDER BY CASE :sort WHEN 'default_minions_count' THEN default_minions_count END, 
            CASE :sort WHEN 'name' THEN scenario.name END, 
            CASE :sort WHEN 'id' THEN scenario.id END, 
            CASE :sort WHEN 'factory_id' THEN scenario.factory_id END, 
            CASE :sort WHEN 'enabled' THEN scenario.enabled END
            """
    )
    @Join(value = "dags", type = Join.Type.LEFT)
    suspend fun findAllActiveWithSorting(tenant: String, sort: String?): List<ScenarioEntity>
}