package io.qalipsis.core.head.jdbc.repository

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity

/**
 * Micronaut data repository to operate with [CampaignScenarioEntity].
 *
 * @author Eric Jessé
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
internal interface CampaignScenarioRepository : CoroutineCrudRepository<CampaignScenarioEntity, Long>