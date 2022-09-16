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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils
import java.time.Duration
import java.time.Instant

internal class ScenarioReportMessageRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var campaignReportRepository: CampaignReportRepository

    @Inject
    private lateinit var scenarioReportRepository: ScenarioReportRepository

    @Inject
    private lateinit var scenarioReportMessageRepository: ScenarioReportMessageRepository

    @Inject
    private lateinit var tenantRepository: TenantRepository

    @Inject
    private lateinit var campaignRepository: CampaignRepository

    lateinit var messagePrototype: ScenarioReportMessageEntity

    @BeforeEach
    fun setUp() = testDispatcherProvider.run {
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
        val campaignPrototype =
            CampaignEntity(
                key = "the-campaign-id",
                name = "This is a campaign",
                speedFactor = 123.0,
                scheduledMinions = 345,
                start = Instant.now() - Duration.ofSeconds(173),
                end = Instant.now(),
                result = ExecutionStatus.SUCCESSFUL,
                configurer = 1
            )
        val campaingEntity = campaignRepository.save(campaignPrototype.copy(tenantId = tenant.id))
        val campaignReportPrototype =
            CampaignReportEntity(
                campaignId = campaingEntity.id,
                startedMinions = 1000,
                completedMinions = 990,
                successfulExecutions = 990,
                failedExecutions = 10,
                status = ExecutionStatus.SUCCESSFUL
            )
        val campaignReportEntity = campaignReportRepository.save(campaignReportPrototype)
        val scenarioReportPrototype =
            ScenarioReportEntity(
                name = RandomStringUtils.randomAlphanumeric(7),
                campaignReportId = campaignReportEntity.id,
                start = Instant.now().minusSeconds(900),
                end = Instant.now().minusSeconds(600),
                startedMinions = 1000,
                completedMinions = 990,
                successfulExecutions = 990,
                failedExecutions = 10,
                status = ExecutionStatus.SUCCESSFUL
            )
        val scenarioReport = scenarioReportRepository.save(scenarioReportPrototype)
        messagePrototype = ScenarioReportMessageEntity(
            scenarioReportId = scenarioReport.id,
            stepName = "my-step",
            messageId = "my-message-1",
            severity = ReportMessageSeverity.INFO,
            message = "This is the first message"
        )
    }

    @AfterEach
    fun tearDown() = testDispatcherProvider.run {
        scenarioReportMessageRepository.deleteAll()
        tenantRepository.deleteAll()
    }

    @AfterAll
    fun tearDownAll() = testDispatcherProvider.run {
        scenarioReportRepository.deleteAll()
        campaignReportRepository.deleteAll()
        campaignRepository.deleteAll()
    }

    @Test
    fun `should save then get`() = testDispatcherProvider.run {
        // given
        val saved = scenarioReportMessageRepository.save(messagePrototype.copy())

        // when
        val fetched = scenarioReportMessageRepository.findById(saved.id)

        // then
        assertThat(fetched).isNotNull().all {
            prop(ScenarioReportMessageEntity::id).isEqualTo(saved.id)
            prop(ScenarioReportMessageEntity::scenarioReportId).isEqualTo(saved.scenarioReportId)
            prop(ScenarioReportMessageEntity::stepName).isEqualTo(saved.stepName)
            prop(ScenarioReportMessageEntity::messageId).isEqualTo(saved.messageId)
            prop(ScenarioReportMessageEntity::severity).isEqualTo(saved.severity)
            prop(ScenarioReportMessageEntity::message).isEqualTo(saved.message)
        }
    }

    @Test
    fun `should update the version when the message is updated`() = testDispatcherProvider.run {
        // given
        val saved = scenarioReportMessageRepository.save(messagePrototype.copy())

        // when
        val updated = scenarioReportMessageRepository.update(saved)

        // then
        assertThat(updated.version).isGreaterThan(saved.version)
    }

    @Test
    fun `should delete scenario report message on deleteById`() = testDispatcherProvider.run {
        // given
        val saved = scenarioReportMessageRepository.save(messagePrototype.copy())
        assertThat(scenarioReportMessageRepository.findAll().count()).isEqualTo(1)

        // when
        scenarioReportMessageRepository.deleteById(saved.id)

        // then
        assertThat(scenarioReportMessageRepository.findAll().count()).isEqualTo(0)
    }
}