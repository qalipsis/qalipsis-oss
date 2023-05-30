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
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.ReportFileEntity
import java.time.Instant

/**
 * Micronaut's data repository to operate with [ReportFileEntity].
 *
 * @author Francisca Eze.
 */

@R2dbcRepository(dialect = Dialect.POSTGRES)
@Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
internal interface ReportFileRepository : CoroutineCrudRepository<ReportFileEntity, Long> {

    /**
     * Finds a file report in a tenant by its report task reference.
     * The report is returned only if the username of the requestor is same as
     * the user that created the report.
     */
    @Query(
        """
        SELECT 
        * FROM report_file rf
        WHERE rf.report_task_id = :reportTaskReference
        AND EXISTS(
                SELECT 1 
                FROM report_task 
                WHERE id = :reportTaskReference 
                AND creator = :username
                AND EXISTS(
                    SELECT 1 
                    FROM tenant 
                    WHERE reference = :tenant
                )
        )
    """
    )
    fun retrieveReportFileByTenantAndReference(
        tenant: String,
        reportTaskReference: Long,
        username: String
    ): ReportFileEntity?


    /**
     * Deletes all records of report file less than the given minimalFileExpiryDate.
     */
    suspend fun deleteAllByCreationTimestampLessThan(minimalFileExpiryDate: Instant)
}