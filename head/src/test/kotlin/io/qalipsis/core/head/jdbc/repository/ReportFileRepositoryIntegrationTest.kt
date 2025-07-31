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
import assertk.assertions.prop
import com.qalipsis.core.head.jdbc.entity.TenantEntityForTest
import com.qalipsis.core.head.jdbc.entity.UserEntityForTest
import io.qalipsis.core.head.jdbc.entity.ReportEntity
import io.qalipsis.core.head.jdbc.entity.ReportFileEntity
import io.qalipsis.core.head.jdbc.entity.ReportTaskEntity
import io.qalipsis.core.head.model.ReportTaskStatus
import io.qalipsis.core.postgres.AbstractPostgreSQLTest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import jakarta.inject.Inject
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant
import java.time.temporal.ChronoUnit

internal class ReportFileRepositoryIntegrationTest : AbstractPostgreSQLTest() {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Inject
    private lateinit var reportFileRepository: ReportFileRepository

    @Inject
    private lateinit var reportRepository: ReportRepository

    @Inject
    private lateinit var reportTaskRepository: ReportTaskRepository

    @Inject
    private lateinit var userRepository: UserRepositoryForTest

    @Inject
    private lateinit var tenantRepository: TenantRepositoryForTest

    private lateinit var reportFilePrototype: ReportFileEntity

    private lateinit var tenant: TenantEntityForTest

    private lateinit var user: UserEntityForTest

    private lateinit var reportTask: ReportTaskEntity

    private lateinit var fileContent: ByteArray

    @BeforeEach
    fun setup() = testDispatcherProvider.run {
        user = userRepository.save(UserEntityForTest(username = "my-user"))
        tenant = tenantRepository.save(TenantEntityForTest(reference = "my-tenant"))
        val reportPrototype = ReportEntity(
            reference = "report-ref",
            tenantId = tenant.id,
            creatorId = user.id,
            displayName = "my-report-name",
            campaignKeys = listOf("campaign-key1", "campaign-key2"),
            campaignNamesPatterns = listOf("*", "\\w"),
            scenarioNamesPatterns = listOf("\\w"),
            query = "This is the query"
        )
        val reportEntity = reportRepository.save(reportPrototype)
        val reportTaskEntity = ReportTaskEntity(
            reportId = reportEntity.id,
            reference = "report-1",
            tenantReference = tenant.reference,
            creationTimestamp = Instant.now(),
            creator = user.username,
            status = ReportTaskStatus.PENDING,
            updateTimestamp = Instant.now()

        )
        reportTask = reportTaskRepository.save(reportTaskEntity)
        fileContent = byteArrayOf(0xA1.toByte(), 0x2E, 0x38, 0xD4.toByte(), 0x89.toByte(), 0xC3.toByte())
        reportFilePrototype = ReportFileEntity(
            name = "${reportEntity.displayName} ${Instant.now().truncatedTo(ChronoUnit.SECONDS)}",
            creationTimestamp = Instant.now(),
            fileContent = fileContent,
            reportTaskId = reportTask.id
        )
    }

    @AfterEach
    fun tearDown() = testDispatcherProvider.run {
        reportRepository.deleteAll()
        tenantRepository.deleteAll()
        userRepository.deleteAll()
        reportTaskRepository.deleteAll()
        reportFileRepository.deleteAll()
    }

    @Test
    fun `should save then get`() = testDispatcherProvider.run {
        //given
        val saved = reportFileRepository.save(reportFilePrototype)

        //when
        val fetched = reportFileRepository.findById(saved.id)

        //then
        assertThat(fetched).isNotNull().all {
            prop(ReportFileEntity::id).isEqualTo(saved.id)
            prop(ReportFileEntity::creationTimestamp).isEqualTo(saved.creationTimestamp)
            prop(ReportFileEntity::fileContent).isEqualTo(saved.fileContent)
            prop(ReportFileEntity::reportTaskId).isEqualTo(saved.reportTaskId)
        }
    }

    @Test
    fun `should retrieve the file content `() = testDispatcherProvider.run {
        //given
        val saved = reportFileRepository.save(reportFilePrototype)

        //when
        val fetched = reportFileRepository.retrieveReportFileByTenantAndReference(
            tenant.reference,
            saved.reportTaskId,
            user.username
        )

        //then
        assertThat(fetched).isNotNull().all {
            prop(ReportFileEntity::fileContent).isEqualTo(reportFilePrototype.fileContent)
        }
    }

    @Test
    fun `should return null when user is not the creator of the task `() = testDispatcherProvider.run {
        //given
        val saved = reportFileRepository.save(reportFilePrototype)
        val user2 = userRepository.save(UserEntityForTest(username = "my-user2"))

        //when
        val fileContent = reportFileRepository.retrieveReportFileByTenantAndReference(
            tenant.reference,
            saved.reportTaskId,
            user2.username
        )

        //then
        assertNull(fileContent)
    }

    @Test
    fun `should return null when task reference does not exist`() = testDispatcherProvider.run {
        //given
        val user2 = userRepository.save(UserEntityForTest(username = "my-user2"))

        //when
        val fileContent =
            reportFileRepository.retrieveReportFileByTenantAndReference(tenant.reference, 234565L, user2.username)

        //then
        assertNull(fileContent)
    }

    @Test
    fun `should return null when tenant is not known`() = testDispatcherProvider.run {
        //given
        val saved = reportFileRepository.save(reportFilePrototype)

        val user2 = userRepository.save(UserEntityForTest(username = "my-user2"))

        //when
        val fileContent = reportFileRepository.retrieveReportFileByTenantAndReference(
            "unknown-ref",
            saved.reportTaskId,
            user2.username
        )

        //then
        assertNull(fileContent)
    }

    @Test
    fun `should delete outdated report files`() =
        testDispatcherProvider.run {
            //given
            val creationTimestamp = Instant.parse("2023-03-18T13:01:07.445312Z")
            val creationTimestamp2 = Instant.parse("2023-05-18T13:01:07.445312Z")
            val creationTimestamp3 = Instant.now().minus(4, ChronoUnit.DAYS).plusSeconds(1)
            reportFileRepository.save(reportFilePrototype.copy(creationTimestamp = creationTimestamp))
            reportFileRepository.save(reportFilePrototype.copy(creationTimestamp = creationTimestamp2))
            val saved3 = reportFileRepository.save(reportFilePrototype.copy(creationTimestamp = creationTimestamp3))
            val fetchedBeforeDeletion = reportFileRepository.findAll().count()
            assertThat(fetchedBeforeDeletion).isEqualTo(3)

            //when
            reportFileRepository.deleteAllByCreationTimestampLessThan(Instant.now().minus(4, ChronoUnit.DAYS))

            //then
            val fetchedAfterDeletion = reportFileRepository.findAll().toList()
            assertThat(fetchedAfterDeletion).isNotNull().all {
                hasSize(1)
                containsExactlyInAnyOrder(
                    saved3
                )
            }
        }
}