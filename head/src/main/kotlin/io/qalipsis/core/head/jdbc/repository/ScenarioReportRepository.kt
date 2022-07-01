package io.qalipsis.core.head.jdbc.repository

import io.micronaut.context.annotation.Requires
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.ScenarioReportEntity

/**
 * Micronaut data repository to operate with [ScenarioReportEntity].
 *
 * @author Palina Bril
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(notEnv = [ExecutionEnvironments.VOLATILE])
internal interface ScenarioReportRepository : CoroutineCrudRepository<ScenarioReportEntity, Long>