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
import assertk.assertions.containsOnly
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import jakarta.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * @author Joël Valère
 */
internal class CampaignScenarioRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var tenantRepository: TenantRepository

    @Inject
    private lateinit var userRepository: UserRepository

    @Inject
    private lateinit var campaignRepository: CampaignRepository

    @Inject
    private lateinit var campaignScenarioRepository: CampaignScenarioRepository

    private val campaignPrototype =
        CampaignEntity(
            key = "the-campaign-id",
            name = "This is my new campaign",
            speedFactor = 123.0,
            start = Instant.now() - Duration.ofSeconds(173),
            scheduledMinions = 12,
            end = Instant.now(),
            result = ExecutionStatus.SUCCESSFUL,
            configurer = 1L // Default user.
        )

    @AfterEach
    fun tearDown() = testDispatcherProvider.run {
        campaignRepository.deleteAll()
        tenantRepository.deleteAll()
        kotlin.runCatching {
            val allButDefaultUsers = userRepository.findAll().filterNot { it.username == Defaults.USER }.toList()
            if (allButDefaultUsers.isNotEmpty()) {
                userRepository.deleteAll(allButDefaultUsers)
            }
        }
    }

    @Test
    internal fun `should start the created scenario`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "my-tenant"))
        val campaign1 = campaignRepository.save(campaignPrototype.copy(key = "1", tenantId = tenant.id))
        val campaign2 = campaignRepository.save(campaignPrototype.copy(key = "2", tenantId = tenant.id))
        val openScenarioOnCampaign1 =
            campaignScenarioRepository.save(CampaignScenarioEntity(campaign1.id, "the-scenario", minionsCount = 231))
        val otherScenarioOnCampaign1 = campaignScenarioRepository.save(
            CampaignScenarioEntity(
                campaign1.id,
                "the-other-scenario",
                minionsCount = 231
            )
        )
        val openScenarioOnCampaign2 =
            campaignScenarioRepository.save(CampaignScenarioEntity(campaign2.id, "the-scenario", minionsCount = 231))

        // when
        val beforeCall = Instant.now()
        val start = Instant.now().plusSeconds(12)
        delay(50) // Adds a delay because it happens that the time in the DB container is slightly in the past.
        campaignScenarioRepository.start("my-tenant", "1", "the-scenario", start)

        // then
        assertThat(campaignScenarioRepository.findById(openScenarioOnCampaign2.id)).isNotNull()
            .isDataClassEqualTo(openScenarioOnCampaign2)
        assertThat(campaignScenarioRepository.findById(otherScenarioOnCampaign1.id)).isNotNull()
            .isDataClassEqualTo(otherScenarioOnCampaign1)
        assertThat(campaignScenarioRepository.findById(openScenarioOnCampaign1.id)).isNotNull().all {
            prop(CampaignScenarioEntity::version).isGreaterThanOrEqualTo(beforeCall)
            prop(CampaignScenarioEntity::name).isEqualTo("the-scenario")
            prop(CampaignScenarioEntity::start).isEqualTo(start)
        }
    }

    @Test
    internal fun `should not start the started scenario`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "my-tenant"))
        val campaign1 = campaignRepository.save(campaignPrototype.copy(key = "1", tenantId = tenant.id))
        val closedScenarioOnCampaign1 = campaignScenarioRepository.save(
            CampaignScenarioEntity(
                campaign1.id,
                "the-scenario",
                start = Instant.now(),
                minionsCount = 231
            )
        )

        // when
        val beforeCall = Instant.now()
        val start = Instant.now().plusSeconds(12)
        campaignScenarioRepository.start("my-tenant", "1", "the-scenario", start)

        // then
        assertThat(campaignScenarioRepository.findById(closedScenarioOnCampaign1.id)).isNotNull().all {
            prop(CampaignScenarioEntity::version).isEqualTo(closedScenarioOnCampaign1.version)
            prop(CampaignScenarioEntity::name).isEqualTo("the-scenario")
            prop(CampaignScenarioEntity::start).isNotNull().isNotEqualTo(start)
        }
    }

    @Test
    internal fun `should complete the open scenario`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "my-tenant"))
        val campaign1 = campaignRepository.save(campaignPrototype.copy(key = "1", tenantId = tenant.id))
        val campaign2 = campaignRepository.save(campaignPrototype.copy(key = "2", tenantId = tenant.id))
        val openScenarioOnCampaign1 =
            campaignScenarioRepository.save(CampaignScenarioEntity(campaign1.id, "the-scenario", minionsCount = 231))
        val otherScenarioOnCampaign1 = campaignScenarioRepository.save(
            CampaignScenarioEntity(
                campaign1.id,
                "the-other-scenario",
                minionsCount = 231
            )
        )
        val openScenarioOnCampaign2 =
            campaignScenarioRepository.save(CampaignScenarioEntity(campaign2.id, "the-scenario", minionsCount = 231))

        // when
        val beforeCall = Instant.now()
        delay(50) // Adds a delay because it happens that the time in the DB container is slightly in the past.
        campaignScenarioRepository.complete("my-tenant", "1", "the-scenario")

        // then
        assertThat(campaignScenarioRepository.findById(openScenarioOnCampaign2.id)).isNotNull()
            .isDataClassEqualTo(openScenarioOnCampaign2)
        assertThat(campaignScenarioRepository.findById(otherScenarioOnCampaign1.id)).isNotNull()
            .isDataClassEqualTo(otherScenarioOnCampaign1)
        assertThat(campaignScenarioRepository.findById(openScenarioOnCampaign1.id)).isNotNull().all {
            prop(CampaignScenarioEntity::version).isGreaterThanOrEqualTo(beforeCall)
            prop(CampaignScenarioEntity::name).isEqualTo("the-scenario")
            prop(CampaignScenarioEntity::end).isNotNull().isGreaterThanOrEqualTo(beforeCall)
        }
    }

    @Test
    internal fun `should not complete the already closed scenario`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "my-tenant"))
        val campaign1 = campaignRepository.save(campaignPrototype.copy(key = "1", tenantId = tenant.id))
        val closedScenarioOnCampaign1 = campaignScenarioRepository.save(
            CampaignScenarioEntity(
                campaign1.id,
                "the-scenario",
                end = Instant.now(),
                minionsCount = 231
            )
        )

        // when
        val beforeCall = Instant.now()
        delay(50) // Adds a delay because it happens that the time in the DB container is slightly in the past.
        campaignScenarioRepository.complete("my-tenant", "1", "the-scenario")

        // then
        assertThat(campaignScenarioRepository.findById(closedScenarioOnCampaign1.id)).isNotNull().all {
            prop(CampaignScenarioEntity::version).isEqualTo(closedScenarioOnCampaign1.version)
            prop(CampaignScenarioEntity::name).isEqualTo("the-scenario")
            prop(CampaignScenarioEntity::end).isNotNull().isLessThanOrEqualTo(beforeCall)
        }
    }

    @Test
    internal fun `should return campaign scenario name by scenario patterns names and campaign keys`() =
        testDispatcherProvider.run {
            //given
            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref", "my-tenant"))
            val tenant2 = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref2", "my-tenant2"))
            val saved = campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-1",
                    name = "campaign-1",
                    end = null,
                    tenantId = tenant.id
                )
            )
            val saved2 = campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-2",
                    name = "campaign-2",
                    end = null,
                    tenantId = tenant.id
                )
            )
            val saved3 = campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-3",
                    name = "campaign-3",
                    end = null,
                    tenantId = tenant.id
                )
            )
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-4",
                    name = "campaign-4",
                    end = null,
                    tenantId = tenant2.id
                )
            )
            campaignScenarioRepository.save(CampaignScenarioEntity(saved.id, "the-scenario", minionsCount = 231))
            campaignScenarioRepository.save(CampaignScenarioEntity(saved.id, "SCENARIO-2", minionsCount = 232))
            campaignScenarioRepository.save(CampaignScenarioEntity(saved2.id, "scenario-3", minionsCount = 233))
            campaignScenarioRepository.save(CampaignScenarioEntity(saved3.id, "SCenaRIo", minionsCount = 234))

            //when + then
            assertThat(
                campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(
                    tenant.id,
                    listOf("%sce%"),
                    listOf("key-2")
                )
            ).containsOnly("scenario-3")
            assertThat(
                campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(
                    tenant.id,
                    listOf("%sce%"),
                    listOf("key-3", "key-2")
                )
            ).containsOnly("scenario-3", "SCenaRIo")
            assertThat(
                campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(
                    tenant.id,
                    listOf("%sce%"),
                    listOf("key-3", "key-2", "key-1")
                )
            ).containsOnly("scenario-3", "SCenaRIo", "SCENARIO-2", "the-scenario")
            assertThat(
                campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(
                    tenant.id,
                    listOf("sce%"),
                    listOf("key-3", "key-2", "key-1")
                )
            ).containsOnly("scenario-3", "SCenaRIo", "SCENARIO-2")
            assertThat(
                campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(
                    tenant.id,
                    listOf("%rio-_"),
                    listOf("key-3", "key-2", "key-1")
                )
            ).containsOnly("scenario-3", "SCENARIO-2")
            assertThat(
                campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(
                    tenant.id,
                    listOf("%rio-_"),
                    listOf("key-3")
                )
            ).isEmpty()
            assertThat(
                campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(
                    tenant.id,
                    listOf("%"),
                    listOf("key-3", "key-2", "key-1")
                )
            ).isNotEmpty()
            assertThat(
                campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(
                    -1,
                    listOf("%"),
                    listOf("key-3", "key-2", "key-1")
                )
            ).isEmpty()
        }

    @Test
    internal fun `should return campaign scenario name by campaign keys`() = testDispatcherProvider.run {
        //given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref", "my-tenant"))
        val tenant2 = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref2", "my-tenant2"))
        val saved = campaignRepository.save(
            campaignPrototype.copy(
                key = "key-1",
                name = "campaign-1",
                end = null,
                tenantId = tenant.id
            )
        )
        val saved2 = campaignRepository.save(
            campaignPrototype.copy(
                key = "key-2",
                name = "campaign-2",
                end = null,
                tenantId = tenant.id
            )
        )
        campaignRepository.save(
            campaignPrototype.copy(
                key = "key-4",
                name = "campaign-4",
                end = null,
                tenantId = tenant2.id
            )
        )
        campaignScenarioRepository.save(CampaignScenarioEntity(saved.id, "the-scenario", minionsCount = 231))
        campaignScenarioRepository.save(CampaignScenarioEntity(saved.id, "SCENARIO-2", minionsCount = 232))
        campaignScenarioRepository.save(CampaignScenarioEntity(saved2.id, "scenario-3", minionsCount = 233))
        campaignScenarioRepository.save(CampaignScenarioEntity(saved.id, "SCenaRIo", minionsCount = 234))

        //when + then
        assertThat(
            campaignScenarioRepository.findNameByCampaignKeys(
                tenant.id,
                listOf("key-1")
            )
        ).containsOnly("SCenaRIo", "SCENARIO-2", "the-scenario")
        assertThat(campaignScenarioRepository.findNameByCampaignKeys(tenant.id, listOf("key-2", "key-1"))).containsOnly(
            "scenario-3",
            "SCenaRIo",
            "SCENARIO-2",
            "the-scenario"
        )
        assertThat(
            campaignScenarioRepository.findNameByCampaignKeys(
                tenant.id,
                listOf("key-2")
            )
        ).containsOnly("scenario-3")
        assertThat(campaignScenarioRepository.findNameByCampaignKeys(-1, listOf("key-2"))).isEmpty()
    }
}