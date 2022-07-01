package io.qalipsis.core.head.jdbc.repository

import io.micronaut.context.annotation.Requires
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity

/**
 * Micronaut data repository to operate with [CampaignScenarioEntity].
 *
 * @author Eric Jessé
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(notEnv = [ExecutionEnvironments.VOLATILE])
internal interface CampaignScenarioRepository : CoroutineCrudRepository<CampaignScenarioEntity, Long> {

    suspend fun findByCampaignId(campaignId: Long): Collection<CampaignScenarioEntity>

}