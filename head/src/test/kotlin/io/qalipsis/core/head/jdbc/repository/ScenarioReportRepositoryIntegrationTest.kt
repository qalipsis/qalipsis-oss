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
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportMessageEntity
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import jakarta.inject.Inject
import kotlinx.coroutines.flow.count
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils
import java.time.Duration
import java.time.Instant

internal class ScenarioReportRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var campaignReportRepository: CampaignReportRepository

    @Inject
    private lateinit var scenarioReportRepository: ScenarioReportRepository

    @Inject
    private lateinit var scenarioReportMessageRepository: ScenarioReportMessageRepository

    @Inject
    private lateinit var campaignRepository: CampaignRepository

    @Inject
    private lateinit var tenantRepository: TenantRepository

    private lateinit var scenarioReportPrototype: ScenarioReportEntity

    @BeforeEach
    fun setUp() = testDispatcherProvider.run {
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
        val campaign = campaignRepository.save(
            CampaignEntity(
                tenantId = tenant.id,
                key = "the-campaign-id",
                name = "This is a campaign",
                scheduledMinions = 345,
                speedFactor = 123.0,
                start = Instant.now() - Duration.ofSeconds(173),
                end = Instant.now(),
                result = ExecutionStatus.SUCCESSFUL,
                configurer = 1
            )
        )
        val campaignReport = campaignReportRepository.save(
            CampaignReportEntity(
                campaignId = campaign.id,
                startedMinions = 1000,
                completedMinions = 990,
                successfulExecutions = 990,
                failedExecutions = 10,
                status = ExecutionStatus.SUCCESSFUL
            )
        )

        scenarioReportPrototype =
            ScenarioReportEntity(
                name = RandomStringUtils.randomAlphanumeric(7),
                campaignReportId = campaignReport.id,
                start = Instant.now().minusSeconds(900),
                end = Instant.now().minusSeconds(600),
                startedMinions = 1000,
                completedMinions = 990,
                successfulExecutions = 990,
                failedExecutions = 10,
                ExecutionStatus.SUCCESSFUL
            )
    }

    @AfterEach
    fun tearDown() = testDispatcherProvider.run {
        scenarioReportRepository.deleteAll()
        campaignReportRepository.deleteAll()
        campaignRepository.deleteAll()
        tenantRepository.deleteAll()
    }

    @Test
    fun `should save then get`() = testDispatcherProvider.run {
        // given
        val saved = scenarioReportRepository.save(scenarioReportPrototype.copy())

        // when
        val fetched = scenarioReportRepository.findById(saved.id)

        // then
        assertThat(fetched).isNotNull().all {
            prop(ScenarioReportEntity::id).isEqualTo(saved.id)
            prop(ScenarioReportEntity::campaignReportId).isEqualTo(saved.campaignReportId)
            prop(ScenarioReportEntity::startedMinions).isEqualTo(saved.startedMinions)
            prop(ScenarioReportEntity::completedMinions).isEqualTo(saved.completedMinions)
            prop(ScenarioReportEntity::successfulExecutions).isEqualTo(saved.successfulExecutions)
            prop(ScenarioReportEntity::failedExecutions).isEqualTo(saved.failedExecutions)
            prop(ScenarioReportEntity::messages).isEqualTo(saved.messages)
            prop(ScenarioReportEntity::status).isEqualTo(saved.status)
        }
        scenarioReportRepository.delete(saved)
    }

    @Test
    fun `should update the version when the scenario report is updated`() = testDispatcherProvider.run {
        // given
        val saved = scenarioReportRepository.save(scenarioReportPrototype.copy())

        // when
        val updated = scenarioReportRepository.update(saved)

        // then
        assertThat(updated.version).isGreaterThan(saved.version)
        scenarioReportRepository.delete(updated)
    }

    @Test
    fun `should delete all the sub-entities on delete`() = testDispatcherProvider.run {
        // given
        val saved = scenarioReportRepository.save(scenarioReportPrototype.copy())
        val messagePrototype = ScenarioReportMessageEntity(
            scenarioReportId = saved.id,
            stepName = "my-step",
            messageId = "my-message-1",
            severity = ReportMessageSeverity.INFO,
            message = "This is the first message"
        )
        scenarioReportMessageRepository.save(messagePrototype.copy())
        assertThat(scenarioReportRepository.findAll().count()).isEqualTo(1)
        assertThat(scenarioReportMessageRepository.findAll().count()).isEqualTo(1)

        // when
        scenarioReportRepository.deleteById(saved.id)

        // then
        assertThat(scenarioReportRepository.findAll().count()).isEqualTo(0)
        assertThat(scenarioReportMessageRepository.findAll().count()).isEqualTo(0)
    }
}