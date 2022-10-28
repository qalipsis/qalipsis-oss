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

package io.qalipsis.core.head.jdbc.repository

import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.ScenarioEntity

/**
 * Micronaut data async repository to operate with scenario entity
 *
 * @author rklymenko
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
internal interface ScenarioRepository : CoroutineCrudRepository<ScenarioEntity, Long> {

    @Query("UPDATE scenario SET enabled = FALSE WHERE id = :id")
    override suspend fun deleteById(id: Long): Int

    @Query("UPDATE scenario SET enabled = FALSE WHERE id = :id")
    override suspend fun delete(entity: ScenarioEntity): Int

    @Query("UPDATE scenario SET enabled = FALSE WHERE id IN (:id)")
    override suspend fun deleteAll(entities: Iterable<ScenarioEntity>): Int

    @Query(
        """SELECT *, 
            dag.id as dag_id, dag.version as dag_version, dag.scenario_id as dag_scenario_id, dag.name as dag_name,
            dag.root as dag_root, dag.singleton as dag_singleton, dag.under_load as dag_under_load, dag.number_of_steps as dag_number_of_steps
            FROM scenario 
            LEFT JOIN directed_acyclic_graph as dag ON scenario.id = dag.scenario_id
            INNER JOIN factory ON scenario.factory_id = factory.id WHERE scenario.name in (:names) AND scenario.enabled = true AND EXISTS (SELECT * FROM tenant WHERE reference = :tenant AND id = factory.tenant_id)
        """
    )
    @Join(value = "dags", alias = "dag_")
    suspend fun findActiveByName(tenant: String, names: Collection<String>): List<ScenarioEntity>

    @Query(
        """SELECT *, 
            dag.id as dag_id, dag.version as dag_version, dag.scenario_id as dag_scenario_id, dag.name as dag_name,
            dag.root as dag_root, dag.singleton as dag_singleton, dag.under_load as dag_under_load, dag.number_of_steps as dag_number_of_steps
            FROM scenario 
            LEFT JOIN directed_acyclic_graph as dag ON scenario.id = dag.scenario_id
            INNER JOIN factory ON factory_id = factory.id WHERE factory_id = :factoryId AND EXISTS (SELECT * FROM tenant WHERE reference = :tenant AND id = factory.tenant_id)"""
    )
    @Join(value = "dags", alias = "dag_")
    suspend fun findByFactoryId(tenant: String, factoryId: Long): List<ScenarioEntity>

    @Query(
        """SELECT *, 
            dag.id as dag_id, dag.version as dag_version, dag.scenario_id as dag_scenario_id, dag.name as dag_name,
            dag.root as dag_root, dag.singleton as dag_singleton, dag.under_load as dag_under_load, dag.number_of_steps as dag_number_of_steps
            FROM scenario 
            LEFT JOIN directed_acyclic_graph as dag ON scenario.id = dag.scenario_id
            INNER JOIN factory ON factory_id = factory.id 
            WHERE enabled = true 
            AND EXISTS -- The factory should be healthy as latest known state within the last 2 minutes.
                (SELECT * FROM factory_state fs WHERE fs.factory_id = factory.id AND fs.state = 'IDLE' and fs.health_timestamp > (now() - interval '${HEALTH_QUERY_INTERVAL}')
                AND NOT EXISTS (SELECT * FROM factory_state WHERE factory_id = factory.id AND state <> 'IDLE' and health_timestamp > fs.health_timestamp))
            AND EXISTS (SELECT * FROM tenant WHERE reference = :tenant AND id = factory.tenant_id) 
            ORDER BY CASE :sort WHEN 'default_minions_count' THEN default_minions_count END, 
            CASE :sort WHEN 'name' THEN scenario.name END, 
            CASE :sort WHEN 'id' THEN scenario.id END 
            """
    )
    @Join(value = "dags", alias = "dag_")
    suspend fun findAllActiveWithSorting(tenant: String, sort: String?): List<ScenarioEntity>

    private companion object {

        const val HEALTH_QUERY_INTERVAL = "2 minute"

    }
}