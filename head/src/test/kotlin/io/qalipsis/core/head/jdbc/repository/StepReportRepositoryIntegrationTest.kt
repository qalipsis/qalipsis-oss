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

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import com.qalipsis.core.head.jdbc.entity.TenantEntityForTest
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportEntity
import io.qalipsis.core.head.jdbc.entity.StepReportEntity
import io.qalipsis.core.postgres.AbstractPostgreSQLTest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant

internal class StepReportRepositoryIntegrationTest : AbstractPostgreSQLTest() {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Inject
    private lateinit var tenantRepository: TenantRepositoryForTest

    @Inject
    private lateinit var campaignRepository: CampaignRepository

    @Inject
    private lateinit var campaignReportRepository: CampaignReportRepository

    @Inject
    private lateinit var scenarioReportRepository: ScenarioReportRepository

    @Inject
    private lateinit var stepReportRepository: StepReportRepository

    private lateinit var scenarioReport1: ScenarioReportEntity
    private lateinit var scenarioReport2: ScenarioReportEntity
    private lateinit var scenarioReport3: ScenarioReportEntity

    @BeforeEach
    fun setUp() = testDispatcherProvider.run {
        val tenant = tenantRepository.save(TenantEntityForTest(reference = "my-tenant"))
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
        val scenarioReportPrototype = ScenarioReportEntity(
            name = "scenario-1",
            campaignReportId = campaignReport.id,
            start = Instant.now().minusSeconds(900),
            end = Instant.now().minusSeconds(600),
            startedMinions = 1000,
            completedMinions = 990,
            successfulExecutions = 990,
            failedExecutions = 10,
            status = ExecutionStatus.SUCCESSFUL
        )
        scenarioReport1 = scenarioReportRepository.save(scenarioReportPrototype.copy(name = "scenario-1"))
        scenarioReport2 = scenarioReportRepository.save(scenarioReportPrototype.copy(name = "scenario-2"))
        scenarioReport3 = scenarioReportRepository.save(scenarioReportPrototype.copy(name = "scenario-3"))
    }

    @AfterEach
    fun tearDown() = testDispatcherProvider.run {
        stepReportRepository.deleteAll()
        scenarioReportRepository.deleteAll()
        campaignReportRepository.deleteAll()
        campaignRepository.deleteAll()
        tenantRepository.deleteAll()
    }

    @Test
    fun `findByScenarioReportIdIn should return step reports for matching scenario report ids`() =
        testDispatcherProvider.run {
            // given
            val step1 = stepReportRepository.save(
                StepReportEntity(
                    scenarioReportId = scenarioReport1.id,
                    name = "step-1",
                    dagId = "dag-1",
                    isUnderLoad = true,
                    initialized = true,
                    successfulExecutions = 100L,
                    failedExecutions = 5L
                )
            )
            val step2 = stepReportRepository.save(
                StepReportEntity(
                    scenarioReportId = scenarioReport2.id,
                    name = "step-2",
                    dagId = "dag-2",
                    isUnderLoad = false,
                    initialized = true,
                    successfulExecutions = 200L,
                    failedExecutions = 0L
                )
            )
            stepReportRepository.save(
                StepReportEntity(
                    scenarioReportId = scenarioReport3.id,
                    name = "step-3",
                    dagId = "dag-3",
                    isUnderLoad = true,
                    initialized = true
                )
            )

            // when
            val result = stepReportRepository.findByScenarioReportIdIn(listOf(scenarioReport1.id, scenarioReport2.id))

            // then
            assertThat(result.map { it.id }).containsExactlyInAnyOrder(step1.id, step2.id)
        }

    @Test
    fun `findByScenarioReportIdIn should return all steps when multiple steps belong to same scenario`() =
        testDispatcherProvider.run {
            // given
            val step1a = stepReportRepository.save(
                StepReportEntity(
                    scenarioReportId = scenarioReport1.id,
                    name = "step-1a",
                    dagId = "dag-1",
                    isUnderLoad = true,
                    initialized = true
                )
            )
            val step1b = stepReportRepository.save(
                StepReportEntity(
                    scenarioReportId = scenarioReport1.id,
                    name = "step-1b",
                    dagId = "dag-1",
                    isUnderLoad = true,
                    initialized = true
                )
            )
            stepReportRepository.save(
                StepReportEntity(
                    scenarioReportId = scenarioReport2.id,
                    name = "step-2",
                    dagId = "dag-2",
                    isUnderLoad = false,
                    initialized = false
                )
            )

            // when
            val result = stepReportRepository.findByScenarioReportIdIn(listOf(scenarioReport1.id))

            // then
            assertThat(result.map { it.id }).containsExactlyInAnyOrder(step1a.id, step1b.id)
        }

    @Test
    fun `findByScenarioReportIdIn should preserve step report fields`() = testDispatcherProvider.run {
        // given
        val step = stepReportRepository.save(
            StepReportEntity(
                scenarioReportId = scenarioReport1.id,
                name = "my-step",
                dagId = "my-dag",
                isUnderLoad = true,
                initialized = false,
                initializationError = "startup failed",
                successfulExecutions = 77L,
                failedExecutions = 3L
            )
        )

        // when
        val result = stepReportRepository.findByScenarioReportIdIn(listOf(scenarioReport1.id))

        // then
        assertThat(result).isEqualTo(listOf(step))
        assertThat(result.first()).prop(StepReportEntity::scenarioReportId).isEqualTo(scenarioReport1.id)
        assertThat(result.first()).prop(StepReportEntity::name).isEqualTo("my-step")
        assertThat(result.first()).prop(StepReportEntity::dagId).isEqualTo("my-dag")
        assertThat(result.first()).prop(StepReportEntity::isUnderLoad).isEqualTo(true)
        assertThat(result.first()).prop(StepReportEntity::initialized).isEqualTo(false)
        assertThat(result.first()).prop(StepReportEntity::initializationError).isEqualTo("startup failed")
        assertThat(result.first()).prop(StepReportEntity::successfulExecutions).isEqualTo(77L)
        assertThat(result.first()).prop(StepReportEntity::failedExecutions).isEqualTo(3L)
    }

    @Test
    fun `findByScenarioReportIdIn should return empty list when no ids match`() = testDispatcherProvider.run {
        // given
        stepReportRepository.save(
            StepReportEntity(
                scenarioReportId = scenarioReport1.id,
                name = "step-1",
                dagId = "dag-1",
                isUnderLoad = true,
                initialized = true
            )
        )

        // when
        val result = stepReportRepository.findByScenarioReportIdIn(listOf(-1L, -2L))

        // then
        assertThat(result).isEmpty()
    }

    @Test
    fun `findByScenarioReportIdIn should return empty list for empty input`() = testDispatcherProvider.run {
        // given
        stepReportRepository.save(
            StepReportEntity(
                scenarioReportId = scenarioReport1.id,
                name = "step-1",
                dagId = "dag-1",
                isUnderLoad = true,
                initialized = true
            )
        )

        // when
        val result = stepReportRepository.findByScenarioReportIdIn(emptyList())

        // then
        assertThat(result).isEmpty()
    }
}
