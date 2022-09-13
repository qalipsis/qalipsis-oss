package io.qalipsis.core.head.jdbc.repository

import io.micronaut.context.annotation.Requires
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.ScenarioReportMessageEntity

/**
 * Micronaut data repository to operate with [ScenarioReportMessageEntity].
 *
 * @author Palina Bril
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
internal interface ScenarioReportMessageRepository : CoroutineCrudRepository<ScenarioReportMessageEntity, Long> {

    suspend fun findByScenarioReportIdInOrderById(scenarioReportsIds: Collection<Long>): List<ScenarioReportMessageEntity>

}