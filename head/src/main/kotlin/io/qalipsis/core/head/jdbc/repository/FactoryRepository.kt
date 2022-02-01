package io.qalipsis.core.head.jdbc.repository

import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.head.jdbc.entity.FactoryEntity

/**
 * Micronaut data async repository to operate with factory entity
 *
 * @author rklymenko
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
internal interface FactoryRepository : CoroutineCrudRepository<FactoryEntity, Long> {

    @Join(value = "selectors", type = Join.Type.LEFT_FETCH)
    suspend fun findByNodeIdIn(factoryIds: Collection<String>): List<FactoryEntity>

    suspend fun findIdByNodeIdIn(factoryIds: Collection<String>): List<Long>

    /**
     * Searching for the factories currently supporting the expected scenarios, and being healthy in the past 2 minutes.
     */
    @Query(
        """SELECT factory.*, 
            factory_selector.id as selectors_id, factory_selector.factory_id as selectors_factory_id, factory_selector.key as selectors_key, factory_selector.value as selectors_value
            FROM factory LEFT JOIN factory_selector ON factory_id = factory.id WHERE
            EXISTS (SELECT * FROM scenario WHERE factory_id = factory.id AND name in (:names) AND enabled = true)
            AND EXISTS -- The factory should be healthy as latest known state within  the last 2 minutes.
                (SELECT * FROM factory_state healthy WHERE factory_id = factory.id AND state = 'HEALTHY' and health_timestamp > (now() - interval '$HEALTH_QUERY_INTERVAL')
                    AND NOT EXISTS (SELECT * FROM factory_state WHERE factory_id = factory.id AND state <> 'HEALTHY' and health_timestamp > healthy.health_timestamp))
            AND NOT EXISTS -- The factory should not be used in a running campaign.
                (SELECT * FROM campaign WHERE "end" IS NULL 
                    AND EXISTS (SELECT * FROM campaign_factory WHERE factory_id = factory.id AND campaign_id = campaign.id AND discarded = false)
                )"""
    )
    @Join(value = "selectors", type = Join.Type.LEFT_FETCH)
    suspend fun getAvailableFactoriesForScenarios(names: Collection<String>): List<FactoryEntity>

    private companion object {

        const val HEALTH_QUERY_INTERVAL = "2 minutes"

    }
}