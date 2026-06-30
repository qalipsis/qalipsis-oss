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
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import com.qalipsis.core.head.jdbc.entity.TenantEntityForTest
import com.qalipsis.core.head.jdbc.entity.UserEntityForTest
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportEntity
import io.qalipsis.core.postgres.AbstractPostgreSQLTest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import jakarta.inject.Inject
import kotlinx.coroutines.flow.count
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant

internal class CampaignReportRepositoryIntegrationTest : AbstractPostgreSQLTest() {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Inject
    private lateinit var campaignReportRepository: CampaignReportRepository

    @Inject
    private lateinit var scenarioReportRepository: ScenarioReportRepository

    @Inject
    private lateinit var campaignRepository: CampaignRepository

    @Inject
    private lateinit var userRepository: UserRepositoryForTest

    @Inject
    private lateinit var tenantRepository: TenantRepositoryForTest

    private lateinit var campaignReportPrototype: CampaignReportEntity

    @BeforeEach
    fun init() = testDispatcherProvider.run {
        val savedUser = userRepository.save(UserEntityForTest(username = "my-user"))
        val tenant = tenantRepository.save(TenantEntityForTest("my-tenant"))
        val campaignPrototype =
            CampaignEntity(
                key = "the-campaign-id",
                name = "This is a campaign",
                speedFactor = 123.0,
                scheduledMinions = 345,
                start = Instant.now() - Duration.ofSeconds(173),
                end = Instant.now(),
                result = ExecutionStatus.SUCCESSFUL,
                tenantId = tenant.id,
                configurer = savedUser.id
            )
        val campaign = campaignRepository.save(campaignPrototype.copy())
        campaignReportPrototype =
            CampaignReportEntity(
                campaignId = campaign.id,
                startedMinions = 1000,
                completedMinions = 990,
                successfulExecutions = 990,
                failedExecutions = 10,
                ExecutionStatus.SUCCESSFUL
            )
    }

    @AfterEach
    fun tearDown() = testDispatcherProvider.run {
        campaignReportRepository.deleteAll()
        tenantRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `should save then get`() = testDispatcherProvider.run {
        // given
        val saved = campaignReportRepository.save(campaignReportPrototype.copy())

        // when
        val fetched = campaignReportRepository.findById(saved.id)

        // then
        assertThat(fetched).isNotNull().all {
            prop(CampaignReportEntity::id).isEqualTo(saved.id)
            prop(CampaignReportEntity::campaignId).isEqualTo(saved.campaignId)
            prop(CampaignReportEntity::startedMinions).isEqualTo(saved.startedMinions)
            prop(CampaignReportEntity::completedMinions).isEqualTo(saved.completedMinions)
            prop(CampaignReportEntity::successfulExecutions).isEqualTo(saved.successfulExecutions)
            prop(CampaignReportEntity::failedExecutions).isEqualTo(saved.failedExecutions)
            prop(CampaignReportEntity::scenariosReports).isEqualTo(saved.scenariosReports)
        }
    }

    @Test
    fun `should update the version when the campaign report is updated`() = testDispatcherProvider.run {
        // given
        val saved = campaignReportRepository.save(campaignReportPrototype.copy())

        // when
        val updated = campaignReportRepository.update(saved)

        // then
        assertThat(updated.version).isGreaterThan(saved.version)
    }

    @Test
    fun `should delete all the sub-entities on delete`() = testDispatcherProvider.run {
        // given
        val saved = campaignReportRepository.save(campaignReportPrototype.copy())
        val scenarioReportPrototype = ScenarioReportEntity(
            name = "first",
            campaignReportId = saved.id,
            start = Instant.now().minusSeconds(900),
            end = Instant.now().minusSeconds(600),
            startedMinions = 1000,
            completedMinions = 990,
            successfulExecutions = 990,
            failedExecutions = 10,
            status = ExecutionStatus.SUCCESSFUL
        )
        scenarioReportRepository.save(scenarioReportPrototype.copy())
        assertThat(scenarioReportRepository.findAll().count()).isEqualTo(1)
        assertThat(campaignReportRepository.findAll().count()).isEqualTo(1)

        // when
        campaignReportRepository.deleteById(saved.id)

        // then
        assertThat(scenarioReportRepository.findAll().count()).isEqualTo(0)
        assertThat(campaignReportRepository.findAll().count()).isEqualTo(0)
    }

    @Test
    fun `should retrieve by campaign id`() = testDispatcherProvider.run {
        // given
        val savedUser = userRepository.save(UserEntityForTest(username = "my-user-2"))
        val tenant = tenantRepository.save(TenantEntityForTest(reference = "my-tenant-2"))
        val campaign = CampaignEntity(
            key = "campaign-1",
            name = "campaign 1",
            scheduledMinions = 345,
            configurer = savedUser.id,
            tenantId = tenant.id
        )
        val savedCampaign = campaignRepository.save(campaign)
        val saved = campaignReportRepository.save(campaignReportPrototype.copy(campaignId = savedCampaign.id))
        campaignReportRepository.save(campaignReportPrototype.copy())

        // when
        val fetched = campaignReportRepository.findByCampaignId(saved.campaignId)

        // then
        assertThat(fetched).isNotNull().all {
            prop(CampaignReportEntity::id).isEqualTo(saved.id)
            prop(CampaignReportEntity::campaignId).isEqualTo(savedCampaign.id)
        }
    }

    @Test
    fun `should not retrieve by campaign id when it does not exist`() = testDispatcherProvider.run {
        // when
        val fetched = campaignReportRepository.findByCampaignId(-1)

        // then
        assertThat(fetched).isNull()
    }

    @Test
    fun `findByCampaignIdIn should return reports for matching campaign ids`() = testDispatcherProvider.run {
        // given
        val user1 = userRepository.save(UserEntityForTest(username = "user-bulk-1"))
        val user2 = userRepository.save(UserEntityForTest(username = "user-bulk-2"))
        val user3 = userRepository.save(UserEntityForTest(username = "user-bulk-3"))
        val tenant1 = tenantRepository.save(TenantEntityForTest(reference = "tenant-bulk-1"))
        val tenant2 = tenantRepository.save(TenantEntityForTest(reference = "tenant-bulk-2"))
        val tenant3 = tenantRepository.save(TenantEntityForTest(reference = "tenant-bulk-3"))
        val campaign1 = campaignRepository.save(
            CampaignEntity(
                key = "bulk-camp-1",
                name = "Bulk 1",
                scheduledMinions = 1,
                tenantId = tenant1.id,
                configurer = user1.id
            )
        )
        val campaign2 = campaignRepository.save(
            CampaignEntity(
                key = "bulk-camp-2",
                name = "Bulk 2",
                scheduledMinions = 1,
                tenantId = tenant2.id,
                configurer = user2.id
            )
        )
        val campaign3 = campaignRepository.save(
            CampaignEntity(
                key = "bulk-camp-3",
                name = "Bulk 3",
                scheduledMinions = 1,
                tenantId = tenant3.id,
                configurer = user3.id
            )
        )
        val report1 = campaignReportRepository.save(campaignReportPrototype.copy(campaignId = campaign1.id))
        val report2 = campaignReportRepository.save(campaignReportPrototype.copy(campaignId = campaign2.id))
        campaignReportRepository.save(campaignReportPrototype.copy(campaignId = campaign3.id))

        // when
        val result = campaignReportRepository.findByCampaignIdIn(listOf(campaign1.id, campaign2.id))

        // then
        assertThat(result.map { it.id }).containsExactlyInAnyOrder(report1.id, report2.id)
    }

    @Test
    fun `findByCampaignIdIn should return empty list when no ids match`() = testDispatcherProvider.run {
        // given
        campaignReportRepository.save(campaignReportPrototype.copy())

        // when
        val result = campaignReportRepository.findByCampaignIdIn(listOf(-1L, -2L))

        // then
        assertThat(result).isEmpty()
    }

    @Test
    fun `findByCampaignIdIn should return empty list for empty input`() = testDispatcherProvider.run {
        // given
        campaignReportRepository.save(campaignReportPrototype.copy())

        // when
        val result = campaignReportRepository.findByCampaignIdIn(emptyList())

        // then
        assertThat(result).isEmpty()
    }
}