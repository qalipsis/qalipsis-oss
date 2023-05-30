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

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import io.qalipsis.core.head.jdbc.entity.ReportEntity
import io.qalipsis.core.head.jdbc.entity.ReportTaskEntity
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.qalipsis.core.head.model.ReportTaskStatus
import jakarta.inject.Inject
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit


internal class ReportTaskRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var reportTaskRepository: ReportTaskRepository

    @Inject
    private lateinit var reportRepository: ReportRepository

    @Inject
    private lateinit var userRepository: UserRepository

    @Inject
    private lateinit var tenantRepository: TenantRepository

    private lateinit var reportTaskPrototype: ReportTaskEntity

    private lateinit var savedUser: UserEntity

    private lateinit var tenant: TenantEntity

    @BeforeEach
    fun setup() = testDispatcherProvider.run {
        savedUser = userRepository.save(UserEntity(displayName = "dis-user", username = "my-user"))
        tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
        val reportPrototype = ReportEntity(
            reference = "report-ref",
            tenantId = tenant.id,
            creatorId = savedUser.id,
            displayName = "my-report-name",
            campaignKeys = listOf("campaign-key1", "campaign-key2"),
            campaignNamesPatterns = listOf("*", "\\w"),
            scenarioNamesPatterns = listOf("\\w"),
            query = "This is the query"
        )
        val reportEntity = reportRepository.save(reportPrototype)
        reportTaskPrototype = ReportTaskEntity(
            reportId = reportEntity.id,
            reference = "report-1",
            tenantReference = tenant.reference,
            creationTimestamp = Instant.now(),
            creator = savedUser.username,
            status = ReportTaskStatus.PENDING,
            updateTimestamp = Instant.now()
        )
    }

    @AfterEach
    fun tearDown() = testDispatcherProvider.run {
        tenantRepository.deleteAll()
        userRepository.deleteAll()
        reportRepository.deleteAll()
        reportTaskRepository.deleteAll()
    }

    @Test
    fun `should save then get`() = testDispatcherProvider.run {
        //given
        val saved = reportTaskRepository.save(reportTaskPrototype)

        //when
        val fetched = reportTaskRepository.findById(saved.id)

        //then
        assertThat(fetched).isNotNull().all {
            prop(ReportTaskEntity::id).isEqualTo(saved.id)
            prop(ReportTaskEntity::creationTimestamp).isEqualTo(saved.creationTimestamp)
            prop(ReportTaskEntity::updateTimestamp).isEqualTo(saved.updateTimestamp)
            prop(ReportTaskEntity::reportId).isEqualTo(saved.reportId)
            prop(ReportTaskEntity::creator).isEqualTo(saved.creator)
            prop(ReportTaskEntity::status).isEqualTo(saved.status)
            prop(ReportTaskEntity::tenantReference).isEqualTo(saved.tenantReference)
        }

    }

    @Test
    fun `should return the Report task matching the task, creator and tenant reference`() = testDispatcherProvider.run {
        //given
        val saved = reportTaskRepository.save(reportTaskPrototype)

        //when
        val fetched =
            reportTaskRepository.findByTenantReferenceAndReference(
                tenant.reference,
                saved.reference
            )

        //then
        assertThat(fetched).isNotNull().all {
            prop(ReportTaskEntity::id).isEqualTo(saved.id)
            prop(ReportTaskEntity::creationTimestamp).isEqualTo(saved.creationTimestamp)
            prop(ReportTaskEntity::updateTimestamp).isEqualTo(saved.updateTimestamp)
            prop(ReportTaskEntity::reportId).isEqualTo(saved.reportId)
            prop(ReportTaskEntity::creator).isEqualTo(saved.creator)
            prop(ReportTaskEntity::status).isEqualTo(saved.status)
            prop(ReportTaskEntity::tenantReference).isEqualTo(saved.tenantReference)
        }
    }

    @Test
    fun `should return null for unknown tenant reference`() = testDispatcherProvider.run {
        //given
        val saved = reportTaskRepository.save(reportTaskPrototype)

        //when
        val fetched =
            reportTaskRepository.findByTenantReferenceAndReference(
                "unknown-my-tenant",
                saved.reference
            )

        //then
        assertThat(fetched).isNull()
    }

    @Test
    fun `should return null for unknown task reference`() = testDispatcherProvider.run {
        //given
        reportTaskRepository.save(reportTaskPrototype)

        //when
        val fetched =
            reportTaskRepository.findByTenantReferenceAndReference(
                tenant.reference,
                "random-ref"
            )

        //then
        assertThat(fetched).isNull()
    }

    @Test
    fun `should return the updated Report task matching the task, creator and tenant reference`() =
        testDispatcherProvider.run {
            //given
            val updateTimestamp = Instant.now().plusSeconds(15)
            val saved = reportTaskRepository.save(reportTaskPrototype)
            reportTaskRepository.update(
                reportTaskPrototype.copy(
                    id = saved.id,
                    updateTimestamp = Instant.now().plusSeconds(3),
                    status = ReportTaskStatus.PROCESSING
                )
            )
            reportTaskRepository.update(
                reportTaskPrototype.copy(
                    id = saved.id,
                    updateTimestamp = updateTimestamp,
                    status = ReportTaskStatus.COMPLETED
                )
            )

            //when
            val fetched =
                reportTaskRepository.findByTenantReferenceAndReference(
                    tenant.reference,
                    reportTaskPrototype.reference
                )

            //then
            assertThat(fetched).isNotNull().all {
                prop(ReportTaskEntity::id).isEqualTo(saved.id)
                prop(ReportTaskEntity::creationTimestamp).isEqualTo(saved.creationTimestamp)
                prop(ReportTaskEntity::updateTimestamp).isEqualTo(updateTimestamp)
                prop(ReportTaskEntity::reportId).isEqualTo(saved.reportId)
                prop(ReportTaskEntity::creator).isEqualTo(saved.creator)
                prop(ReportTaskEntity::status).isEqualTo(ReportTaskStatus.COMPLETED)
                prop(ReportTaskEntity::tenantReference).isEqualTo(saved.tenantReference)
            }
        }

    @Test
    fun `should delete outdated report tasks`() =
        testDispatcherProvider.run {
            //given
            val updateTimestamp = Instant.parse("2023-03-18T13:01:07.445312Z")
            val updateTimestamp2 = Instant.parse("2023-05-18T13:01:07.445312Z")
            val updateTimestamp3 = Instant.now().minus(30, ChronoUnit.DAYS).plusSeconds(1)
            reportTaskRepository.save(
                reportTaskPrototype.copy(
                    updateTimestamp = updateTimestamp,
                    reference = "report-task-1"
                )
            )
            val saved2 = reportTaskRepository.save(
                reportTaskPrototype.copy(
                    updateTimestamp = updateTimestamp2,
                    reference = "report-task-2"
                )
            )
            val saved3 = reportTaskRepository.save(reportTaskPrototype.copy(updateTimestamp = updateTimestamp3))
            val fetchedBeforeDeletion = reportTaskRepository.findAll().count()
            assertThat(fetchedBeforeDeletion).isEqualTo(3)

            //when
            reportTaskRepository.deleteAllByUpdateTimestampLessThan(Instant.now().minus(30, ChronoUnit.DAYS))

            //then
            val fetchedAfterDeletion = reportTaskRepository.findAll().toList()
            assertThat(fetchedAfterDeletion).isNotNull().all {
                hasSize(2)
                containsExactlyInAnyOrder(
                    saved2,
                    saved3,
                )
            }
        }
}