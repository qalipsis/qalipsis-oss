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
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.qalipsis.api.report.ExecutionStatus.ABORTED
import io.qalipsis.api.report.ExecutionStatus.FAILED
import io.qalipsis.api.report.ExecutionStatus.IN_PROGRESS
import io.qalipsis.api.report.ExecutionStatus.QUEUED
import io.qalipsis.api.report.ExecutionStatus.SCHEDULED
import io.qalipsis.api.report.ExecutionStatus.SUCCESSFUL
import io.qalipsis.api.report.ExecutionStatus.WARNING
import io.qalipsis.core.head.campaign.scheduler.DailyScheduling
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignFactoryEntity
import io.qalipsis.core.head.jdbc.entity.CampaignReportEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository.CampaignKeyAndName
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.core.head.report.CampaignData
import jakarta.inject.Inject
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test


internal class CampaignRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var factoryRepository: FactoryRepository

    @Inject
    private lateinit var campaignRepository: CampaignRepository

    @Inject
    private lateinit var campaignScenarioRepository: CampaignScenarioRepository

    @Inject
    private lateinit var campaignFactoryRepository: CampaignFactoryRepository

    @Inject
    private lateinit var campaignReportRepository: CampaignReportRepository

    @Inject
    private lateinit var tenantRepository: TenantRepository

    @Inject
    private lateinit var userRepository: UserRepository

    private val campaignPrototype =
        CampaignEntity(
            key = "the-campaign-id",
            name = "This is my new campaign",
            speedFactor = 123.0,
            start = Instant.now() - Duration.ofSeconds(173),
            end = Instant.now(),
            scheduledMinions = 123,
            result = SUCCESSFUL,
            configurer = 1L, // Default user.

        )

    private val tenantPrototype = TenantEntity(Instant.now(), "my-tenant", "test-tenant")

    @AfterEach
    fun tearDown() = testDispatcherProvider.run {
        campaignRepository.deleteAll()
        factoryRepository.deleteAll()
        tenantRepository.deleteAll()
        kotlin.runCatching {
            val allButDefaultUsers = userRepository.findAll().filterNot { it.username == Defaults.USER }.toList()
            if (allButDefaultUsers.isNotEmpty()) {
                userRepository.deleteAll(allButDefaultUsers)
            }
        }
    }

    @Test
    fun `should save then get`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
        val saved = campaignRepository.save(
            campaignPrototype.copy(
                tenantId = tenant.id,
                failureReason = "This is the failure",
                zones = setOf("fr", "at")
            )
        )

        // when
        val fetched = campaignRepository.findById(saved.id)

        // then
        assertThat(fetched).isNotNull().isDataClassEqualTo(saved)
    }

    @Test
    fun `should update the aborter and the result`() = testDispatcherProvider.run {
        // given
        val instant = Instant.now()
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
        val aborter = userRepository.save(UserEntity(username = "John Doe"))
        val saved = campaignRepository.save(
            campaignPrototype.copy(
                tenantId = tenant.id,
                failureReason = "This is the failure",
                zones = setOf("fr", "at"),
                start = instant.plusSeconds(1),
                end = null
            )
        )

        // when
        val fetched = campaignRepository.update(saved.copy(aborter = aborter.id, result = ABORTED))

        // then
        assertThat(fetched).isNotNull().all {
            prop(CampaignEntity::key).isEqualTo("the-campaign-id")
            prop(CampaignEntity::name).isEqualTo("This is my new campaign")
            prop(CampaignEntity::scheduledMinions).isEqualTo(123)
            prop(CampaignEntity::tenantId).isEqualTo(tenant.id)
            prop(CampaignEntity::speedFactor).isEqualTo(123.0)
            prop(CampaignEntity::start).isEqualTo(instant.plusSeconds(1))
            prop(CampaignEntity::end).isEqualTo(null)
            prop(CampaignEntity::aborter).isEqualTo(aborter.id)
            prop(CampaignEntity::result).isEqualTo(ABORTED)
            prop(CampaignEntity::configurer).isEqualTo(1L)
            prop(CampaignEntity::failureReason).isEqualTo("This is the failure")
            prop(CampaignEntity::zones).isEqualTo(setOf("fr", "at"))
        }
    }

    @Test
    fun `should find the ID of the running campaign`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val saved = campaignRepository.save(campaignPrototype.copy(tenantId = tenant.id))

        // when + then
        assertNull(campaignRepository.findIdByTenantAndKeyAndEndIsNull("my-tenant", saved.key))

        // when
        campaignRepository.update(saved.copy(end = null))

        assertThat(campaignRepository.findIdByTenantAndKeyAndEndIsNull("my-tenant", saved.key)).isEqualTo(saved.id)
    }

    @Test
    fun `should find the ID of the running campaign and different tenants aren't mixed up`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy())
            val tenant2 = tenantRepository.save(tenantPrototype.copy(reference = "qalipsis-2"))
            val saved =
                campaignRepository.save(campaignPrototype.copy(key = "1", end = null, tenantId = tenant.id))
            val saved2 =
                campaignRepository.save(
                    campaignPrototype.copy(
                        key = "2",
                        name = "new",
                        end = null,
                        tenantId = tenant2.id
                    )
                )

            // when + then
            assertThat(campaignRepository.findIdByTenantAndKeyAndEndIsNull("my-tenant", saved.key)).isEqualTo(saved.id)

            assertThat(
                campaignRepository.findIdByTenantAndKeyAndEndIsNull("qalipsis-2", saved2.key)
            ).isEqualTo(saved2.id)

            assertNull(campaignRepository.findIdByTenantAndKeyAndEndIsNull("my-tenant", saved2.key))

            assertNull(campaignRepository.findIdByTenantAndKeyAndEndIsNull("qalipsis-2", saved.key))
        }

    @Test
    fun `should update the version and aborter when the campaign is updated`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val saved = campaignRepository.save(campaignPrototype.copy(tenantId = tenant.id))

        // when
        val updated = campaignRepository.update(saved.copy(aborter = 1))

        // then
        assertThat(updated.version).isGreaterThan(saved.version)
        assertThat(saved.aborter).isNull()
        assertThat(updated.aborter).isEqualTo(1)
    }

    @Test
    fun `should delete all the sub-entities on delete`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val saved = campaignRepository.save(campaignPrototype.copy(tenantId = tenant.id))
        val factory =
            factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-node-id",
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "the-registration-node-id",
                    unicastChannel = "unicast-channel",
                    tenantId = tenant.id
                )
            )
        campaignScenarioRepository.save(CampaignScenarioEntity(saved.id, "the-scenario", minionsCount = 231))
        campaignFactoryRepository.save(CampaignFactoryEntity(saved.id, factory.id, discarded = false))

        assertThat(campaignScenarioRepository.findAll().count()).isEqualTo(1)
        assertThat(campaignFactoryRepository.findAll().count()).isEqualTo(1)

        // when
        campaignRepository.deleteById(saved.id)

        // then
        assertThat(campaignScenarioRepository.findAll().count()).isEqualTo(0)
        assertThat(campaignFactoryRepository.findAll().count()).isEqualTo(0)
    }

    @Test
    fun `should mark the campaign as prepared`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val alreadyClosedCampaign =
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "1",
                    start = Instant.now(),
                    tenantId = tenant.id,
                    end = null,
                    result = null
                )
            )
        val openCampaign = campaignRepository.save(
            campaignPrototype.copy(
                key = "2",
                start = null,
                tenantId = tenant.id,
                end = null,
                result = null
            )
        )
        val otherOpenCampaign =
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "3",
                    name = "other-campaign",
                    start = null,
                    tenantId = tenant.id,
                    end = null,
                    result = null
                )
            )

        // when
        val beforeCall = Instant.now()
        delay(50) // Adds a delay because it happens that the time in the DB container is slightly in the past.
        campaignRepository.prepare("my-tenant", "2")

        // then
        assertThat(campaignRepository.findById(alreadyClosedCampaign.id)).isNotNull()
            .isDataClassEqualTo(alreadyClosedCampaign)
        assertThat(campaignRepository.findById(otherOpenCampaign.id)).isNotNull().isDataClassEqualTo(otherOpenCampaign)
        assertThat(campaignRepository.findById(openCampaign.id)).isNotNull().all {
            prop(CampaignEntity::version).isGreaterThanOrEqualTo(beforeCall)
            prop(CampaignEntity::name).isEqualTo(openCampaign.name)
            prop(CampaignEntity::start).isNull()
            prop(CampaignEntity::softTimeout).isNull()
            prop(CampaignEntity::hardTimeout).isNull()
            prop(CampaignEntity::speedFactor).isEqualTo(openCampaign.speedFactor)
            prop(CampaignEntity::end).isNull()
            prop(CampaignEntity::result).isEqualTo(IN_PROGRESS)
        }
    }

    @Test
    fun `should start the created campaign with a timeout`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val alreadyClosedCampaign =
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "1",
                    start = Instant.now(),
                    tenantId = tenant.id,
                    end = null,
                    result = null
                )
            )
        val openCampaign = campaignRepository.save(
            campaignPrototype.copy(
                key = "2",
                start = null,
                tenantId = tenant.id,
                end = null,
                result = null
            )
        )
        val otherOpenCampaign =
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "3",
                    name = "other-campaign",
                    start = null,
                    tenantId = tenant.id,
                    end = null,
                    result = null
                )
            )

        // when
        val beforeCall = Instant.now()
        val start = Instant.now().plusSeconds(12)
        delay(50) // Adds a delay because it happens that the time in the DB container is slightly in the past.
        campaignRepository.start("my-tenant", "2", start, start.plusSeconds(123), null)

        // then
        assertThat(campaignRepository.findById(alreadyClosedCampaign.id)).isNotNull()
            .isDataClassEqualTo(alreadyClosedCampaign)
        assertThat(campaignRepository.findById(otherOpenCampaign.id)).isNotNull().isDataClassEqualTo(otherOpenCampaign)
        assertThat(campaignRepository.findById(openCampaign.id)).isNotNull().all {
            prop(CampaignEntity::version).isGreaterThanOrEqualTo(beforeCall)
            prop(CampaignEntity::name).isEqualTo(openCampaign.name)
            prop(CampaignEntity::start).isEqualTo(start)
            prop(CampaignEntity::softTimeout).isEqualTo(start.plusSeconds(123))
            prop(CampaignEntity::speedFactor).isEqualTo(openCampaign.speedFactor)
            prop(CampaignEntity::end).isNull()
            prop(CampaignEntity::result).isNull()
        }
    }


    @Test
    fun `should start the created campaign without timeout`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val alreadyClosedCampaign =
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "1",
                    start = Instant.now(),
                    tenantId = tenant.id,
                    end = null,
                    result = null
                )
            )
        val openCampaign = campaignRepository.save(
            campaignPrototype.copy(
                key = "2",
                start = null,
                tenantId = tenant.id,
                end = null,
                result = null
            )
        )
        val otherOpenCampaign =
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "3",
                    name = "other-campaign",
                    start = null,
                    tenantId = tenant.id,
                    end = null,
                    result = null
                )
            )

        // when
        val beforeCall = Instant.now()
        val start = Instant.now().plusSeconds(12)
        delay(50) // Adds a delay because it happens that the time in the DB container is slightly in the past.
        campaignRepository.start("my-tenant", "2", start, null, null)

        // then
        assertThat(campaignRepository.findById(alreadyClosedCampaign.id)).isNotNull()
            .isDataClassEqualTo(alreadyClosedCampaign)
        assertThat(campaignRepository.findById(otherOpenCampaign.id)).isNotNull().isDataClassEqualTo(otherOpenCampaign)
        assertThat(campaignRepository.findById(openCampaign.id)).isNotNull().all {
            prop(CampaignEntity::version).isGreaterThanOrEqualTo(beforeCall)
            prop(CampaignEntity::name).isEqualTo(openCampaign.name)
            prop(CampaignEntity::start).isEqualTo(start)
            prop(CampaignEntity::softTimeout).isNull()
            prop(CampaignEntity::hardTimeout).isNull()
            prop(CampaignEntity::speedFactor).isEqualTo(openCampaign.speedFactor)
            prop(CampaignEntity::end).isNull()
            prop(CampaignEntity::result).isNull()
        }
    }

    @Test
    fun `should complete the open campaign with a message`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val alreadyClosedCampaign =
            campaignRepository.save(campaignPrototype.copy(key = "1", end = Instant.now(), tenantId = tenant.id))
        val openCampaign = campaignRepository.save(campaignPrototype.copy(key = "2", end = null, tenantId = tenant.id))
        val otherOpenCampaign =
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "3",
                    name = "other-campaign",
                    end = null,
                    tenantId = tenant.id
                )
            )

        // when
        val beforeCall = Instant.now()
        delay(50) // Adds a delay because it happens that the time in the DB container is slightly in the past.
        campaignRepository.complete("my-tenant", "2", FAILED, "The campaign fails")

        // then
        assertThat(campaignRepository.findById(alreadyClosedCampaign.id)).isNotNull()
            .isDataClassEqualTo(alreadyClosedCampaign)
        assertThat(campaignRepository.findById(otherOpenCampaign.id)).isNotNull().isDataClassEqualTo(otherOpenCampaign)
        assertThat(campaignRepository.findById(openCampaign.id)).isNotNull().all {
            prop(CampaignEntity::version).isGreaterThanOrEqualTo(beforeCall)
            prop(CampaignEntity::name).isEqualTo(openCampaign.name)
            prop(CampaignEntity::start).isEqualTo(openCampaign.start)
            prop(CampaignEntity::speedFactor).isEqualTo(openCampaign.speedFactor)
            prop(CampaignEntity::end).isNotNull().isGreaterThanOrEqualTo(beforeCall)
            prop(CampaignEntity::result).isEqualTo(FAILED)
            prop(CampaignEntity::failureReason).isEqualTo("The campaign fails")
        }
    }


    @Test
    fun `should complete the open campaign without message`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val alreadyClosedCampaign =
            campaignRepository.save(campaignPrototype.copy(key = "1", end = Instant.now(), tenantId = tenant.id))
        val openCampaign = campaignRepository.save(
            campaignPrototype.copy(
                key = "2", end = null, tenantId = tenant.id,
                failureReason = "The initial reason"
            )
        )
        val otherOpenCampaign =
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "3",
                    name = "other-campaign",
                    end = null,
                    tenantId = tenant.id
                )
            )

        // when
        val beforeCall = Instant.now()
        delay(50) // Adds a delay because it happens that the time in the DB container is slightly in the past.
        campaignRepository.complete("my-tenant", "2", SUCCESSFUL, null)

        // then
        assertThat(campaignRepository.findById(alreadyClosedCampaign.id)).isNotNull()
            .isDataClassEqualTo(alreadyClosedCampaign)
        assertThat(campaignRepository.findById(otherOpenCampaign.id)).isNotNull().isDataClassEqualTo(otherOpenCampaign)
        assertThat(campaignRepository.findById(openCampaign.id)).isNotNull().all {
            prop(CampaignEntity::version).isGreaterThanOrEqualTo(beforeCall)
            prop(CampaignEntity::name).isEqualTo(openCampaign.name)
            prop(CampaignEntity::start).isEqualTo(openCampaign.start)
            prop(CampaignEntity::speedFactor).isEqualTo(openCampaign.speedFactor)
            prop(CampaignEntity::end).isNotNull().isGreaterThanOrEqualTo(beforeCall)
            prop(CampaignEntity::result).isEqualTo(SUCCESSFUL)
            prop(CampaignEntity::failureReason).isEqualTo("The initial reason")
        }
    }

    @Test
    fun `should find all campaigns in tenant without filter but with paging`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy(reference = "my-tenant-2"))
            val saved = campaignRepository.save(campaignPrototype.copy(key = "1", end = null, tenantId = tenant.id))
            campaignScenarioRepository.saveAll(
                listOf(
                    CampaignScenarioEntity(saved.id, name = "scenario 1", minionsCount = 2),
                    CampaignScenarioEntity(saved.id, name = "scenario 2", minionsCount = 2)
                )
            )
            val saved2 = campaignRepository.save(campaignPrototype.copy(key = "2", end = null, tenantId = tenant.id))

            // when + then
            assertThat(
                campaignRepository.findAll(
                    "my-tenant-2",
                    Pageable.from(0, 1, Sort.of(Sort.Order("key")))
                )
            ).all {
                prop(Page<*>::getTotalSize).isEqualTo(2)
                prop(Page<*>::getTotalPages).isEqualTo(2)
                prop(Page<*>::getContent).containsOnly(saved)
            }
            assertThat(
                campaignRepository.findAll(
                    "my-tenant-2",
                    Pageable.from(1, 1, Sort.of(Sort.Order("key")))
                )
            ).all {
                prop(Page<*>::getTotalSize).isEqualTo(2)
                prop(Page<*>::getTotalPages).isEqualTo(2)
                prop(Page<*>::getContent).containsOnly(saved2)
            }
        }

    @Test
    fun `should find all campaigns in tenant with filter and paging`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy(reference = "my-tenant-2"))
            campaignRepository.save(campaignPrototype.copy(end = null, tenantId = tenant.id))
            val saved2 =
                campaignRepository.save(campaignPrototype.copy(key = "anyone-1", end = null, tenantId = tenant.id))
            campaignScenarioRepository.saveAll(
                listOf(
                    CampaignScenarioEntity(saved2.id, name = "scenario 1", minionsCount = 2),
                    CampaignScenarioEntity(saved2.id, name = "scenario 2", minionsCount = 2)
                )
            )
            val saved3 =
                campaignRepository.save(campaignPrototype.copy(key = "anyone-2", end = null, tenantId = tenant.id))

            // when + then
            assertThat(
                campaignRepository.findAll(
                    "my-tenant-2",
                    listOf("%NyO%", "%NoNe%"),
                    Pageable.from(0, 2, Sort.of(Sort.Order("key")))
                )
            ).all {
                prop(Page<*>::getTotalSize).isEqualTo(2)
                prop(Page<*>::getTotalPages).isEqualTo(1)
                prop(Page<*>::getContent).containsOnly(saved2, saved3)
            }
            assertThat(
                campaignRepository.findAll(
                    "my-tenant-2",
                    listOf("%NyO%", "%NoNe%"),
                    Pageable.from(0, 2, Sort.of(Sort.Order("key", Sort.Order.Direction.DESC, true)))
                )
            ).all {
                prop(Page<*>::getTotalSize).isEqualTo(2)
                prop(Page<*>::getTotalPages).isEqualTo(1)
                prop(Page<*>::getContent).containsOnly(saved2, saved3)
            }
            assertThat(
                campaignRepository.findAll(
                    "my-tenant-2",
                    listOf("%NyO%", "%NoNe%"),
                    Pageable.from(0, 1, Sort.of(Sort.Order("key")))
                )
            ).all {
                prop(Page<*>::getTotalSize).isEqualTo(2)
                prop(Page<*>::getTotalPages).isEqualTo(2)
                prop(Page<*>::getContent).containsOnly(saved2)
            }
            assertThat(
                campaignRepository.findAll(
                    "my-tenant-2",
                    listOf("%NyO%", "%NoNe%"),
                    Pageable.from(1, 1, Sort.of(Sort.Order("key")))
                )
            ).all {
                prop(Page<*>::getTotalSize).isEqualTo(2)
                prop(Page<*>::getTotalPages).isEqualTo(2)
                prop(Page<*>::getContent).containsOnly(saved3)
            }

            assertThat(
                campaignRepository.findAll("other-tenant", listOf("%NyO%", "%NoNe%"), Pageable.from(0, 1))
            ).isEmpty()
        }

    @Test
    fun `should find all campaigns in tenant with filter on key`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy(reference = "my-tenant-2"))
            campaignRepository.save(campaignPrototype.copy(end = null, tenantId = tenant.id))
            val saved2 =
                campaignRepository.save(campaignPrototype.copy(key = "anyone", end = null, tenantId = tenant.id))
            campaignScenarioRepository.saveAll(
                listOf(
                    CampaignScenarioEntity(saved2.id, name = "scenario 1", minionsCount = 2),
                    CampaignScenarioEntity(saved2.id, name = "scenario 2", minionsCount = 2)
                )
            )

            // when + then
            assertThat(
                campaignRepository.findAll("my-tenant-2", listOf("%NyO%", "%NoNe%"), Pageable.from(0, 1))
            ).all {
                prop(Page<*>::getTotalSize).isEqualTo(1)
                prop(Page<*>::getTotalPages).isEqualTo(1)
                prop(Page<*>::getContent).containsOnly(saved2)
            }
            assertThat(
                campaignRepository.findAll("other-tenant", listOf("%NyO%", "%NoNe%"), Pageable.from(0, 1))
            ).isEmpty()
        }

    @Test
    fun `should find all campaigns in tenant with filter on name`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy(reference = "my-tenant-2"))
            campaignRepository.save(campaignPrototype.copy(end = null, tenantId = tenant.id))
            val saved2 = campaignRepository.save(
                campaignPrototype.copy(
                    key = "the-key",
                    name = "The other name",
                    end = null,
                    tenantId = tenant.id
                )
            )
            campaignScenarioRepository.saveAll(
                listOf(
                    CampaignScenarioEntity(saved2.id, name = "scenario 1", minionsCount = 2),
                    CampaignScenarioEntity(saved2.id, name = "scenario 2", minionsCount = 2)
                )
            )

            // when + then
            assertThat(
                campaignRepository.findAll(
                    "my-tenant-2",
                    listOf("%OtH%", "%NoNe%"),
                    Pageable.from(0, 1)
                )
            ).all {
                prop(Page<*>::getTotalSize).isEqualTo(1)
                prop(Page<*>::getTotalPages).isEqualTo(1)
                prop(Page<*>::getContent).containsOnly(saved2)
            }
            assertThat(
                campaignRepository.findAll(
                    "other-tenant",
                    listOf("%OtH%", "%NoNe%"),
                    Pageable.from(0, 1)
                )
            ).isEmpty()
        }

    @Test
    fun `should find all campaigns in tenant with filter on configurer username`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy(reference = "my-tenant-2"))
            val user = userRepository.save(UserEntity(username = "John Doe"))
            campaignRepository.save(campaignPrototype.copy(end = null, tenantId = tenant.id))
            val saved2 = campaignRepository.save(
                campaignPrototype.copy(
                    key = "the-key",
                    name = "The other name",
                    end = null,
                    tenantId = tenant.id,
                    configurer = user.id
                )
            )
            campaignScenarioRepository.saveAll(
                listOf(
                    CampaignScenarioEntity(saved2.id, name = "scenario 1", minionsCount = 2),
                    CampaignScenarioEntity(saved2.id, name = "scenario 2", minionsCount = 2)
                )
            )

            // when + then
            assertThat(
                campaignRepository.findAll(
                    "my-tenant-2",
                    listOf("%HN%", "%NoNe%"),
                    Pageable.from(0, 1)
                )
            ).all {
                prop(Page<*>::getTotalSize).isEqualTo(1)
                prop(Page<*>::getTotalPages).isEqualTo(1)
                prop(Page<*>::getContent).containsOnly(saved2)
            }
            assertThat(
                campaignRepository.findAll(
                    "other-tenant",
                    listOf("%HN%", "%NoNe%"),
                    Pageable.from(0, 1)
                )
            ).isEmpty()
        }

    @Test
    fun `should find all campaigns in tenant with filter on configurer display name`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy(reference = "my-tenant-2"))
            val user = userRepository.save(UserEntity(username = "foo", displayName = "John Doe"))
            campaignRepository.save(campaignPrototype.copy(end = null, tenantId = tenant.id))
            val saved2 = campaignRepository.save(
                campaignPrototype.copy(
                    key = "the-key",
                    name = "The other name",
                    end = null,
                    tenantId = tenant.id,
                    configurer = user.id
                )
            )
            campaignScenarioRepository.saveAll(
                listOf(
                    CampaignScenarioEntity(saved2.id, name = "scenario 1", minionsCount = 2),
                    CampaignScenarioEntity(saved2.id, name = "scenario 2", minionsCount = 2)
                )
            )

            // when + then
            assertThat(
                campaignRepository.findAll(
                    "my-tenant-2",
                    listOf("%HN%", "%NoNe%"),
                    Pageable.from(0, 1)
                )
            ).all {
                prop(Page<*>::getTotalSize).isEqualTo(1)
                prop(Page<*>::getTotalPages).isEqualTo(1)
                prop(Page<*>::getContent).containsOnly(saved2)
            }
            assertThat(
                campaignRepository.findAll(
                    "other-tenant",
                    listOf("%HN%", "%NoNe%"),
                    Pageable.from(0, 1)
                )
            ).isEmpty()
        }

    @Test
    fun `should find campaign by key`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "tenant-11", "test-tenant"))
        val saved = campaignRepository.save(campaignPrototype.copy(key = "name-11", tenantId = tenant.id))

        // when
        val fetched = campaignRepository.findByTenantAndKey("tenant-11", saved.key)

        // then
        assertThat(fetched).isNotNull().isDataClassEqualTo(saved)
    }

    @Test
    fun `should return only campaign keys of the tenant`() = testDispatcherProvider.run {
        //given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref", "my-tenant"))
        val tenant2 = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref2", "my-tenant2"))
        campaignRepository.save(
            campaignPrototype.copy(
                key = "key-1",
                name = "campaign-1",
                end = null,
                tenantId = tenant.id
            )
        )
        campaignRepository.save(
            campaignPrototype.copy(
                key = "key-2",
                name = "campaign-2",
                end = null,
                tenantId = tenant2.id
            )
        )
        campaignRepository.save(
            campaignPrototype.copy(
                key = "key-3",
                name = "campaign-3",
                end = null,
                tenantId = tenant.id
            )
        )

        // when
        val campaignKeys =
            campaignRepository.findKeyByTenantAndKeyIn(tenant = tenant.reference, keys = listOf("key-1", "key-3"))
        // then
        assertThat(campaignKeys).all {
            hasSize(2)
            containsOnly("key-1", "key-3")
        }

        //when + then
        assertThat(
            campaignRepository.findKeyByTenantAndKeyIn(
                tenant = tenant.reference,
                keys = listOf("key-4", "key-2")
            )
        ).isEmpty()
    }

    @Test
    fun `should return only campaign keys of the tenant and campaign names patterns`() =
        testDispatcherProvider.run {
            //given
            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref", "my-tenant"))
            val tenant2 = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref2", "my-tenant2"))
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-1",
                    name = "campaign-1",
                    end = null,
                    tenantId = tenant.id
                )
            )
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-2",
                    name = "CAMPAIGN-2",
                    end = null,
                    tenantId = tenant2.id
                )
            )
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-3",
                    name = "camp-3",
                    end = null,
                    tenantId = tenant.id
                )
            )
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-4",
                    name = "CAMPAIGN-4",
                    end = null,
                    tenantId = tenant.id
                )
            )

            // when
            val campaignKeys = campaignRepository.findKeysByTenantAndNamePatterns("tenant-ref", listOf("camp%"))
            // then
            assertThat(campaignKeys).all {
                hasSize(3)
                containsOnly("key-1", "key-3", "key-4")
            }

            //when + then
            assertThat(campaignRepository.findKeysByTenantAndNamePatterns("tenant-ref", listOf("camp-_"))).containsOnly(
                "key-3"
            )
            assertThat(
                campaignRepository.findKeysByTenantAndNamePatterns(
                    "tenant-ref",
                    listOf("%IG%")
                )
            ).containsOnly("key-1", "key-4")
            assertThat(
                campaignRepository.findKeysByTenantAndNamePatterns(
                    "tenant-ref",
                    listOf("%IG%", "ca_")
                )
            ).containsOnly("key-1", "key-4")
            assertThat(
                campaignRepository.findKeysByTenantAndNamePatterns(
                    "tenant-ref",
                    listOf("%IG%", "ca%")
                )
            ).containsOnly("key-1", "key-3", "key-4")
            assertThat(
                campaignRepository.findKeysByTenantAndNamePatterns(
                    "tenant-ref",
                    listOf("%4")
                )
            ).containsOnly("key-4")
            assertThat(campaignRepository.findKeysByTenantAndNamePatterns("tenant-ref", listOf("GN%"))).isEmpty()
            assertThat(
                campaignRepository.findKeysByTenantAndNamePatterns(
                    "tenant-ref",
                    listOf("GN%", "%4")
                )
            ).containsOnly("key-4")
        }

    @Test
    fun `should return campaign keys and names by tenant id and campaign names patterns and campaign keys`() =
        testDispatcherProvider.run {
            //given
            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref", "my-tenant"))
            val tenant2 = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref2", "my-tenant2"))
            val instant = Instant.now()
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-1",
                    name = "campaign-1",
                    end = instant.plusMillis(1),
                    tenantId = tenant.id
                )
            )
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-2",
                    name = "CAMPAIGN-2",
                    end = instant.plusMillis(2),
                    tenantId = tenant2.id
                )
            )
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-0",
                    name = "campaign-2",
                    end = instant.plusMillis(2),
                    tenantId = tenant.id
                )
            )
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-3",
                    name = "camp-3",
                    end = instant.plusMillis(3),
                    tenantId = tenant.id
                )
            )
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-4",
                    name = "CAMPAIGN-4",
                    end = instant.plusMillis(4),
                    tenantId = tenant.id
                )
            )
            (5..9).map {
                campaignRepository.save(
                    campaignPrototype.copy(
                        key = "key-$it",
                        name = "campaign-name-$it",
                        end = instant.plusMillis(it.toLong()),
                        tenantId = tenant.id
                    )
                )
            }
            (5..9).map {
                campaignRepository.save(
                    campaignPrototype.copy(
                        key = "$it-key-$it",
                        name = "$it-campaign-name-$it",
                        end = instant.plusMillis((it * it).toLong()),
                        tenantId = tenant.id
                    )
                )
            }

            assertThat(campaignRepository.findAll().count()).isEqualTo(15)

            // when finding by the tenant: my-tenant
            assertThat(
                campaignRepository.findKeysAndNamesByTenantIdAndNamePatternsOrKeys(
                    tenant.id,
                    listOf("%gn-2"),
                    listOf()
                )
            ).all {
                hasSize(1)
                isEqualTo(
                    listOf(
                        CampaignKeyAndName("key-0", "campaign-2")
                    )
                )
            }

            // when finding by the tenant: my-tenant2
            assertThat(
                campaignRepository.findKeysAndNamesByTenantIdAndNamePatternsOrKeys(
                    tenant2.id,
                    listOf("%gn-2"),
                    listOf()
                )
            ).all {
                hasSize(1)
                isEqualTo(
                    listOf(
                        CampaignKeyAndName("key-2", "CAMPAIGN-2")
                    )
                )
            }

            // when finding only by campaign name patterns
            assertThat(
                campaignRepository.findKeysAndNamesByTenantIdAndNamePatternsOrKeys(
                    tenant.id,
                    listOf("camp%"),
                    listOf()
                )
            ).all {
                hasSize(9)
                isEqualTo(
                    listOf(
                        CampaignKeyAndName("key-9", "campaign-name-9"),
                        CampaignKeyAndName("key-8", "campaign-name-8"),
                        CampaignKeyAndName("key-7", "campaign-name-7"),
                        CampaignKeyAndName("key-6", "campaign-name-6"),
                        CampaignKeyAndName("key-5", "campaign-name-5"),
                        CampaignKeyAndName("key-4", "CAMPAIGN-4"),
                        CampaignKeyAndName("key-3", "camp-3"),
                        CampaignKeyAndName("key-0", "campaign-2"),
                        CampaignKeyAndName("key-1", "campaign-1")
                    )
                )
            }

            // when finding only by campaign name patterns and case-sensitive
            assertThat(
                campaignRepository.findKeysAndNamesByTenantIdAndNamePatternsOrKeys(
                    tenant.id,
                    listOf("%IGN%"),
                    listOf()
                )
            ).all {
                hasSize(10)
                isEqualTo(
                    listOf(
                        CampaignKeyAndName("9-key-9", "9-campaign-name-9"),
                        CampaignKeyAndName("8-key-8", "8-campaign-name-8"),
                        CampaignKeyAndName("7-key-7", "7-campaign-name-7"),
                        CampaignKeyAndName("6-key-6", "6-campaign-name-6"),
                        CampaignKeyAndName("5-key-5", "5-campaign-name-5"),
                        CampaignKeyAndName("key-9", "campaign-name-9"),
                        CampaignKeyAndName("key-8", "campaign-name-8"),
                        CampaignKeyAndName("key-7", "campaign-name-7"),
                        CampaignKeyAndName("key-6", "campaign-name-6"),
                        CampaignKeyAndName("key-5", "campaign-name-5")
                    )
                )
            }

            // when finding only by campaign keys
            assertThat(
                campaignRepository.findKeysAndNamesByTenantIdAndNamePatternsOrKeys(
                    tenant.id,
                    listOf(),
                    listOf("9-key-9", "8-key-8", "key-7", "key-6", "key-5")
                )
            ).all {
                hasSize(5)
                isEqualTo(
                    listOf(
                        CampaignKeyAndName("9-key-9", "9-campaign-name-9"),
                        CampaignKeyAndName("8-key-8", "8-campaign-name-8"),
                        CampaignKeyAndName("key-7", "campaign-name-7"),
                        CampaignKeyAndName("key-6", "campaign-name-6"),
                        CampaignKeyAndName("key-5", "campaign-name-5")
                    )
                )
            }

            // when finding both by campaign keys and campaign name patterns (exclusive)
            assertThat(
                campaignRepository.findKeysAndNamesByTenantIdAndNamePatternsOrKeys(
                    tenant.id,
                    listOf("campa%"),
                    listOf("9-key-9")
                )
            ).all {
                hasSize(9)
                isEqualTo(
                    listOf(
                        CampaignKeyAndName("9-key-9", "9-campaign-name-9"),
                        CampaignKeyAndName("key-9", "campaign-name-9"),
                        CampaignKeyAndName("key-8", "campaign-name-8"),
                        CampaignKeyAndName("key-7", "campaign-name-7"),
                        CampaignKeyAndName("key-6", "campaign-name-6"),
                        CampaignKeyAndName("key-5", "campaign-name-5"),
                        CampaignKeyAndName("key-4", "CAMPAIGN-4"),
                        CampaignKeyAndName("key-0", "campaign-2"),
                        CampaignKeyAndName("key-1", "campaign-1")
                    )
                )
            }

            // when finding only by campaign keys and campaign name patterns (inclusive)
            assertThat(
                campaignRepository.findKeysAndNamesByTenantIdAndNamePatternsOrKeys(
                    tenant.id,
                    listOf("camp%"),
                    listOf("key-5", "key-8", "key-7", "key-9")
                )
            ).all {
                hasSize(9)
                isEqualTo(
                    listOf(
                        CampaignKeyAndName("key-9", "campaign-name-9"),
                        CampaignKeyAndName("key-8", "campaign-name-8"),
                        CampaignKeyAndName("key-7", "campaign-name-7"),
                        CampaignKeyAndName("key-6", "campaign-name-6"),
                        CampaignKeyAndName("key-5", "campaign-name-5"),
                        CampaignKeyAndName("key-4", "CAMPAIGN-4"),
                        CampaignKeyAndName("key-3", "camp-3"),
                        CampaignKeyAndName("key-0", "campaign-2"),
                        CampaignKeyAndName("key-1", "campaign-1")
                    )
                )
            }

            // when finding returns only top 10 by campaign.end DESC
            assertThat(
                campaignRepository.findKeysAndNamesByTenantIdAndNamePatternsOrKeys(
                    tenant.id,
                    listOf("camp%"),
                    listOf("9-key-9", "6-key-6", "8-key-8", "7-key-7")
                )
            ).all {
                hasSize(10)
                isEqualTo(
                    listOf(
                        CampaignKeyAndName("9-key-9", "9-campaign-name-9"),
                        CampaignKeyAndName("8-key-8", "8-campaign-name-8"),
                        CampaignKeyAndName("7-key-7", "7-campaign-name-7"),
                        CampaignKeyAndName("6-key-6", "6-campaign-name-6"),
                        CampaignKeyAndName("key-9", "campaign-name-9"),
                        CampaignKeyAndName("key-8", "campaign-name-8"),
                        CampaignKeyAndName("key-7", "campaign-name-7"),
                        CampaignKeyAndName("key-6", "campaign-name-6"),
                        CampaignKeyAndName("key-5", "campaign-name-5"),
                        CampaignKeyAndName("key-4", "CAMPAIGN-4")
                    )
                )
            }

            // when + then
            assertThat(
                campaignRepository.findKeysAndNamesByTenantIdAndNamePatternsOrKeys(
                    tenant.id,
                    listOf("camp-_"),
                    listOf()
                )
            ).all {
                hasSize(1)
                isEqualTo(
                    listOf(CampaignKeyAndName("key-3", "camp-3"))
                )
            }

            // when finding from multiple campaign name patterns
            assertThat(
                campaignRepository.findKeysAndNamesByTenantIdAndNamePatternsOrKeys(
                    tenant.id,
                    listOf("c%IG%", "ca_", "x"),
                    listOf()
                )
            ).all {
                hasSize(8)
                isEqualTo(
                    listOf(
                        CampaignKeyAndName("key-9", "campaign-name-9"),
                        CampaignKeyAndName("key-8", "campaign-name-8"),
                        CampaignKeyAndName("key-7", "campaign-name-7"),
                        CampaignKeyAndName("key-6", "campaign-name-6"),
                        CampaignKeyAndName("key-5", "campaign-name-5"),
                        CampaignKeyAndName("key-4", "CAMPAIGN-4"),
                        CampaignKeyAndName("key-0", "campaign-2"),
                        CampaignKeyAndName("key-1", "campaign-1")
                    )
                )
            }

            // when campaign name patterns do not match
            assertThat(
                campaignRepository.findKeysAndNamesByTenantIdAndNamePatternsOrKeys(
                    tenant.id,
                    listOf("GN%"),
                    listOf()
                )
            ).isEmpty()

            // when campaign name patterns list and campaign keys list are empty
            assertThat(
                campaignRepository.findKeysAndNamesByTenantIdAndNamePatternsOrKeys(
                    tenant.id,
                    listOf(),
                    listOf()
                )
            ).isEmpty()

            // when campaign keys do not match
            assertThat(
                campaignRepository.findKeysAndNamesByTenantIdAndNamePatternsOrKeys(
                    tenant.id,
                    listOf(),
                    listOf("key", "keys-1")
                )
            ).isEmpty()
        }


    @Test
    fun `should return the min start, max end and max duration when all the campaigns are ended`() =
        testDispatcherProvider.run {
            // given
            val start1 = Instant.now().truncatedTo(ChronoUnit.SECONDS) - Duration.ofMinutes(23)
            val end1 = start1 + Duration.ofSeconds(16)

            val start2 = Instant.now().truncatedTo(ChronoUnit.SECONDS) - Duration.ofMinutes(22)
            val end2 = start2 + Duration.ofSeconds(1_236)

            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
            campaignRepository.saveAll(
                listOf(
                    campaignPrototype.copy(tenantId = tenant.id, key = "camp-1", start = start1, end = end1),
                    campaignPrototype.copy(tenantId = tenant.id, key = "camp-2", start = start2, end = end2),
                    // "Noise entities" to verify the clauses.
                    campaignPrototype.copy(
                        tenantId = tenant.id,
                        key = "camp-3",
                        start = Instant.EPOCH,
                        end = Instant.now() + Duration.ofDays(365)
                    )
                )
            ).count()

            // when
            val result = campaignRepository.findInstantsAndDuration("my-tenant", setOf("camp-1", "camp-2"))

            // then
            assertThat(result).isNotNull().all {
                prop(CampaignsInstantsAndDuration::minStart).isEqualTo(start1)
                prop(CampaignsInstantsAndDuration::maxEnd).isEqualTo(end2)
                prop(CampaignsInstantsAndDuration::maxDurationSec).isEqualTo(Duration.between(start2, end2).toSeconds())
            }
        }

    @Test
    fun `should return the min start, max end and max duration when the longest campaigns are not ended`() =
        testDispatcherProvider.run {
            // given
            val start1 = Instant.now().truncatedTo(ChronoUnit.SECONDS) - Duration.ofMinutes(23)
            val end1 = start1 + Duration.ofSeconds(16)

            val start2 = Instant.now().truncatedTo(ChronoUnit.SECONDS) - Duration.ofMinutes(22)

            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
            campaignRepository.saveAll(
                listOf(
                    campaignPrototype.copy(tenantId = tenant.id, key = "camp-1", start = start1, end = end1),
                    campaignPrototype.copy(tenantId = tenant.id, key = "camp-2", start = start2, end = null),
                    // "Noise entities" to verify the clauses.
                    campaignPrototype.copy(
                        tenantId = tenant.id,
                        key = "camp-3",
                        start = Instant.EPOCH,
                        end = Instant.now() + Duration.ofDays(365)
                    )
                )
            ).count()

            // when
            val justBeforeQuery = Instant.now()
            delay(50) // Adds a delay because it happens that the time in the DB container is slightly in the past.
            val result = campaignRepository.findInstantsAndDuration("my-tenant", setOf("camp-1", "camp-2"))

            // then
            assertThat(result).isNotNull().all {
                prop(CampaignsInstantsAndDuration::minStart).isEqualTo(start1)
                prop(CampaignsInstantsAndDuration::maxEnd).isNotNull().isGreaterThanOrEqualTo(justBeforeQuery)
                prop(CampaignsInstantsAndDuration::maxDurationSec).isNotNull()
                    .isGreaterThanOrEqualTo(Duration.ofMinutes(22).toSeconds())
            }
        }

    @Test
    fun `should return neither start, end nor duration when no specified campaign exists`() =
        testDispatcherProvider.run {
            // when
            val result = campaignRepository.findInstantsAndDuration("my-tenant", setOf("camp-1", "camp-2"))

            // then
            assertThat(result).isNotNull().all {
                prop(CampaignsInstantsAndDuration::minStart).isNull()
                prop(CampaignsInstantsAndDuration::maxEnd).isNull()
                prop(CampaignsInstantsAndDuration::maxDurationSec).isNull()
            }
        }

    @Test
    fun `should retrieve the campaign results and their states`() = testDispatcherProvider.run {
        // given
        val savedUser = userRepository.save(UserEntity(displayName = "dis-user-2", username = "my-user-2"))
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant-2", "test-tenant-2"))
        val tenant3 = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant-3", "test-tenant-3"))
        val start = Instant.parse("2022-02-22T00:00:00.00Z")
        val end = Instant.parse("2022-02-26T00:00:00.00Z")
        val interval = Duration.ofHours(24)
        val campaign = CampaignEntity(
            key = "campaign-1",
            name = "campaign 1",
            scheduledMinions = 345,
            configurer = savedUser.id,
            tenantId = tenant.id,
            start = Instant.parse("2022-02-25T00:00:00.00Z"),
            result = SUCCESSFUL,
            end = Instant.parse("2022-03-26T23:12:11.21Z")
        )
        campaignRepository.saveAll(
            listOf(
                campaign,
                campaign.copy(
                    key = "campaign-2",
                    name = "campaign 2",
                    start = Instant.parse("2022-02-27T00:00:00.00Z"),
                    result = ABORTED //SHOULD NOT RETRIEVE BECAUSE OF DATE ABOVE START
                ),
                campaign.copy(
                    key = "campaign-10",
                    name = "campaign 10",
                    start = Instant.parse("2022-02-24T11:25:30.00Z"),
                    result = SCHEDULED
                ),
                campaign.copy(
                    key = "campaign-3",
                    name = "campaign 3",
                    start = Instant.parse("2021-03-22T11:25:30.00Z"),
                    result = WARNING //SHOULD NOT RETRIEVE BECAUSE OF YEAR BELOW START
                ),
                campaign.copy(
                    key = "campaign-9",
                    name = "campaign 9",
                    start = Instant.parse("2022-02-24T00:00:00.00Z"),
                    result = SUCCESSFUL
                ),
                campaign.copy(
                    key = "campaign-4",
                    name = "campaign 4",
                    start = Instant.parse("2022-02-24T14:25:30.00Z"),
                    result = SCHEDULED
                ),
                campaign.copy(
                    key = "campaign-12",
                    name = "campaign 12",
                    start = Instant.parse("2022-03-24T01:01:01.00Z"),
                    result = WARNING //SHOULD NOT RETRIEVE BECAUSE OF MONTH ABOVE START
                ),
                campaign.copy(
                    key = "campaign-5",
                    name = "campaign 5",
                    start = Instant.parse("2022-02-22T11:25:30.00Z"),
                    tenantId = tenant3.id,
                    result = IN_PROGRESS //SHOULD NOT RETRIEVE BECAUSE OF DIFFERENT TENANT
                ),
                campaign.copy(
                    key = "campaign-11",
                    name = "campaign 11",
                    start = Instant.parse("2022-02-22T01:01:01.00Z"),
                    result = WARNING
                ),
                campaign.copy(
                    key = "campaign-6",
                    name = "campaign 6",
                    start = Instant.parse("2022-02-20T11:25:30.00Z"),
                    result = SUCCESSFUL //SHOULD NOT RETRIEVE BECAUSE OF DATE BEHIND START
                ),
                campaign.copy(
                    key = "campaign-7",
                    name = "campaign 7",
                    start = Instant.parse("2022-02-25T00:00:30.00Z"),
                    result = FAILED
                ),
                campaign.copy(
                    key = "campaign-8",
                    name = "campaign 8",
                    start = Instant.parse("2022-02-25T00:00:30.00Z"),
                    result = FAILED
                ),
                campaign.copy(
                    key = "campaign-13",
                    name = "campaign 13",
                    start = Instant.parse("2022-02-25T00:00:00.00Z"),
                    result = IN_PROGRESS,
                    end = null //SHOULD NOT RETRIEVE BECAUSE CAMPAIGN HAS NOT ENDED YET
                )
            )
        ).count()

        // when
        val fetched = campaignRepository.retrieveCampaignsStatusHistogram(
            "my-tenant-2",
            start,
            end,
            interval
        )

        // then
        assertThat(fetched).all {
            hasSize(5)
            containsExactlyInAnyOrder(
                CampaignRepository.CampaignResultCount(
                    seriesStart = Instant.parse("2022-02-22T00:00:00.00Z"),
                    status = WARNING,
                    count = 1
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = Instant.parse("2022-02-24T00:00:00.00Z"),
                    status = SCHEDULED,
                    count = 2
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = Instant.parse("2022-02-24T00:00:00.00Z"),
                    status = SUCCESSFUL,
                    count = 1
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = Instant.parse("2022-02-25T00:00:00.00Z"),
                    status = FAILED,
                    count = 2
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = Instant.parse("2022-02-25T00:00:00.00Z"),
                    status = SUCCESSFUL,
                    count = 1
                )
            )
        }
    }

    @Test
    fun `should return campaigns by their execution status`() = testDispatcherProvider.run {
        //given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref", "my-tenant"))
        val tenant2 = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref2", "my-tenant2"))
        val campaign1 = campaignRepository.save(
            campaignPrototype.copy(
                key = "key-1",
                name = "campaign-1",
                end = null,
                tenantId = tenant.id,
                result = ABORTED
            )
        )
        val campaign2 = campaignRepository.save(
            campaignPrototype.copy(
                key = "key-2",
                name = "campaign-2",
                end = null,
                tenantId = tenant2.id,
                result = SCHEDULED
            )
        )
        val campaign3 = campaignRepository.save(
            campaignPrototype.copy(
                key = "key-3",
                name = "campaign-3",
                end = null,
                tenantId = tenant.id,
                result = SCHEDULED
            )
        )
        val campaign4 = campaignRepository.save(campaignPrototype.copy(tenantId = tenant2.id))

        assertThat(campaignRepository.findAll().count()).isEqualTo(4)

        // when + then
        assertThat(campaignRepository.findByResult(SCHEDULED)).all {
            hasSize(2)
            containsOnly(campaign2, campaign3)
        }
        assertThat(campaignRepository.findByResult(SUCCESSFUL)).all {
            hasSize(1)
            containsOnly(campaign4)
        }
        assertThat(campaignRepository.findByResult(ABORTED)).all {
            hasSize(1)
            containsOnly(campaign1)
        }
        assertThat(campaignRepository.findByResult(IN_PROGRESS)).isEmpty()
    }

    @Test
    fun `should retrieve valid campaign details by their tenant and matching campaign keys in`() =
        testDispatcherProvider.run {
            // given
            val savedUser = userRepository.save(UserEntity(displayName = "dis-user-2", username = "my-user-2"))
            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant-2", "test-tenant-2"))
            val campaign = CampaignEntity(
                key = "campaign-1",
                name = "campaign 1",
                scheduledMinions = 345,
                configurer = savedUser.id,
                tenantId = tenant.id,
                start = Instant.parse("2022-02-25T00:00:00.00Z"),
                result = SUCCESSFUL,
                end = Instant.parse("2022-03-26T23:12:11.21Z")
            )
            val campaign6 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-6",
                    name = "campaign 6",
                    start = Instant.parse("2022-02-20T11:25:30.00Z"),
                    result = SUCCESSFUL,
                )
            )
            val campaign8 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-8",
                    name = "campaign 8",
                    start = Instant.parse("2022-02-20T11:25:30.00Z"),
                    result = SUCCESSFUL,
                )
            )
            val campaign11 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-11",
                    name = "campaign 11",
                    start = Instant.parse("2022-02-20T11:25:30.00Z"),
                    result = SUCCESSFUL,
                )
            )
            val campaign7 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-7",
                    name = "campaign 7",
                    start = Instant.parse("2022-02-25T00:00:30.00Z"),
                    result = FAILED,
                    end = null
                )
            )

            campaignRepository.saveAll(
                listOf(
                    campaign,
                    campaign.copy(
                        key = "campaign-17",
                        name = "campaign 17",
                        start = Instant.parse("2022-02-22T01:01:01.00Z"),
                        result = WARNING
                    ),
                    campaign.copy(
                        key = "campaign-5",
                        name = "campaign 5",
                        start = Instant.parse("2022-02-25T00:00:30.00Z"),
                        result = FAILED
                    ),
                )
            ).count()

            campaignReportRepository.saveAll(
                listOf(
                    CampaignReportEntity(
                        campaignId = campaign6.id,
                        startedMinions = 1000,
                        completedMinions = 990,
                        successfulExecutions = 990,
                        failedExecutions = 10,
                        SUCCESSFUL
                    ),
                    CampaignReportEntity(
                        campaignId = campaign8.id,
                        startedMinions = 300,
                        completedMinions = 110,
                        successfulExecutions = 56,
                        failedExecutions = 20,
                        ABORTED
                    ),
                    CampaignReportEntity(
                        campaignId = campaign11.id,
                        startedMinions = 280,
                        completedMinions = 87,
                        successfulExecutions = 41,
                        failedExecutions = 65,
                        WARNING
                    ),
                    CampaignReportEntity(
                        campaignId = campaign7.id,
                        startedMinions = 455,
                        completedMinions = 400,
                        successfulExecutions = 400,
                        failedExecutions = 55,
                        QUEUED
                    )
                )
            ).count()

            // when
            val fetched = campaignRepository.retrieveCampaignDetailByTenantIdAndKeyIn(
                tenant.id,
                campaignKeys = listOf("campaign-11", "campaign-8", "campaign-6", "campaign-7"),
                campaignNamePatterns = listOf(),
                scenarioNamePatterns = listOf()
            )

            // then
            assertThat(fetched).all {
                hasSize(3)
                containsExactlyInAnyOrder(
                    CampaignData(
                        name = "campaign 8",
                        zones = emptySet(),
                        result = SUCCESSFUL,
                        executionTime = campaign8.start?.let {
                            campaign8.end?.toEpochMilli()?.minus(it.toEpochMilli())
                                ?: 0
                        }!! / 1000,
                        startedMinions = 300,
                        completedMinions = 110,
                        successfulExecutions = 56,
                        failedExecutions = 20,
                    ),
                    CampaignData(
                        name = "campaign 6",
                        zones = emptySet(),
                        result = SUCCESSFUL,
                        executionTime = campaign6.start?.let {
                            campaign6.end?.toEpochMilli()?.minus(it.toEpochMilli())
                                ?: 0
                        }!! / 1000,
                        startedMinions = 1000,
                        completedMinions = 990,
                        successfulExecutions = 990,
                        failedExecutions = 10,
                    ),
                    CampaignData(
                        name = "campaign 11",
                        zones = emptySet(),
                        result = SUCCESSFUL,
                        executionTime = campaign11.start?.let {
                            campaign11.end?.toEpochMilli()?.minus(it.toEpochMilli())
                                ?: 0
                        }!! / 1000,
                        startedMinions = 280,
                        completedMinions = 87,
                        successfulExecutions = 41,
                        failedExecutions = 65,
                    )
                )
            }
        }

    @Test
    fun `should retrieve valid campaign data details by their tenant and matching campaign name patterns in`() =
        testDispatcherProvider.run {
            // given
            val savedUser = userRepository.save(UserEntity(displayName = "dis-user-2", username = "my-user-2"))
            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant-2", "test-tenant-2"))
            val campaign = CampaignEntity(
                key = "campaign-1",
                name = "campaign 1",
                scheduledMinions = 345,
                configurer = savedUser.id,
                tenantId = tenant.id,
                start = Instant.parse("2022-02-25T00:00:00.00Z"),
                result = SUCCESSFUL,
                end = Instant.parse("2022-03-26T23:12:11.21Z")
            )

            val campaign8 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-8",
                    name = "campaign 8",
                    start = Instant.parse("2022-02-20T11:25:30.00Z"),
                    result = SUCCESSFUL,
                )
            )
            val campaign11 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-11",
                    name = "campaign 11",
                    start = Instant.parse("2022-02-20T11:25:30.00Z"),
                    result = SUCCESSFUL,
                )
            )
            val campaign7 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-7",
                    name = "campaign 7",
                    start = Instant.parse("2022-02-25T00:00:30.00Z"),
                    result = FAILED,
                    end = null
                )
            )

            campaignRepository.saveAll(
                listOf(
                    campaign.copy(
                        key = "campaign-5",
                        name = "campaign 5",
                        start = Instant.parse("2022-02-25T00:00:30.00Z"),
                        result = FAILED
                    ),
                )
            ).count()

            campaignReportRepository.saveAll(
                listOf(
                    CampaignReportEntity(
                        campaignId = campaign8.id,
                        startedMinions = 300,
                        completedMinions = 110,
                        successfulExecutions = 56,
                        failedExecutions = 20,
                        ABORTED
                    ),
                    CampaignReportEntity(
                        campaignId = campaign11.id,
                        startedMinions = 280,
                        completedMinions = 87,
                        successfulExecutions = 41,
                        failedExecutions = 65,
                        WARNING
                    ),
                    CampaignReportEntity(
                        campaignId = campaign7.id,
                        startedMinions = 455,
                        completedMinions = 400,
                        successfulExecutions = 400,
                        failedExecutions = 55,
                        QUEUED
                    )
                )
            ).count()

            // when
            val fetched = campaignRepository.retrieveCampaignDetailByTenantIdAndKeyIn(
                tenant.id,
                campaignKeys = listOf(),
                campaignNamePatterns = listOf("campaign 11", "campaign 8", "campaign-7"),
                scenarioNamePatterns = listOf()
            )

            // then
            assertThat(fetched).all {
                hasSize(2)
                containsExactlyInAnyOrder(
                    CampaignData(
                        name = "campaign 8",
                        zones = emptySet(),
                        result = SUCCESSFUL,
                        executionTime = campaign8.start?.let {
                            campaign8.end?.toEpochMilli()?.minus(it.toEpochMilli())
                                ?: 0
                        }!! / 1000,
                        startedMinions = 300,
                        completedMinions = 110,
                        successfulExecutions = 56,
                        failedExecutions = 20,
                    ),
                    CampaignData(
                        name = "campaign 11",
                        zones = emptySet(),
                        result = SUCCESSFUL,
                        executionTime = campaign11.start?.let {
                            campaign11.end?.toEpochMilli()?.minus(it.toEpochMilli())
                                ?: 0
                        }!! / 1000,
                        startedMinions = 280,
                        completedMinions = 87,
                        successfulExecutions = 41,
                        failedExecutions = 65,
                    )
                )
            }
        }

    @Test
    fun `should retrieve valid campaign data details by their tenant and scenario names in`() =
        testDispatcherProvider.run {
            // given
            val savedUser = userRepository.save(UserEntity(displayName = "dis-user-2", username = "my-user-2"))
            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant-2", "test-tenant-2"))
            val campaign = CampaignEntity(
                key = "campaign-1",
                name = "campaign 1",
                scheduledMinions = 345,
                configurer = savedUser.id,
                tenantId = tenant.id,
                start = Instant.parse("2022-02-25T00:00:00.00Z"),
                result = SUCCESSFUL,
                end = Instant.parse("2022-03-26T23:12:11.21Z")
            )
            val campaign6 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-6",
                    name = "campaign 6",
                    start = Instant.parse("2022-02-20T11:25:30.00Z"),
                    result = SUCCESSFUL,
                )
            )
            val campaign8 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-8",
                    name = "campaign 8",
                    start = Instant.parse("2022-02-20T11:25:30.00Z"),
                    result = SUCCESSFUL,
                )
            )
            val campaign11 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-11",
                    name = "campaign 11",
                    start = Instant.parse("2022-02-20T11:25:30.00Z"),
                    result = SUCCESSFUL,
                )
            )
            val campaign7 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-7",
                    name = "campaign 7",
                    start = Instant.parse("2022-02-25T00:00:30.00Z"),
                    result = FAILED,
                    end = null
                )
            )

            campaignRepository.saveAll(
                listOf(
                    campaign,
                    campaign.copy(
                        key = "campaign-17",
                        name = "campaign 17",
                        start = Instant.parse("2022-02-22T01:01:01.00Z"),
                        result = WARNING
                    ),
                    campaign.copy(
                        key = "campaign-5",
                        name = "campaign 5",
                        start = Instant.parse("2022-02-25T00:00:30.00Z"),
                        result = FAILED
                    ),
                )
            ).count()

            campaignReportRepository.saveAll(
                listOf(
                    CampaignReportEntity(
                        campaignId = campaign6.id,
                        startedMinions = 1000,
                        completedMinions = 990,
                        successfulExecutions = 990,
                        failedExecutions = 10,
                        SUCCESSFUL
                    ),
                    CampaignReportEntity(
                        campaignId = campaign8.id,
                        startedMinions = 300,
                        completedMinions = 110,
                        successfulExecutions = 56,
                        failedExecutions = 20,
                        ABORTED
                    ),
                    CampaignReportEntity(
                        campaignId = campaign11.id,
                        startedMinions = 280,
                        completedMinions = 87,
                        successfulExecutions = 41,
                        failedExecutions = 65,
                        WARNING
                    ),
                    CampaignReportEntity(
                        campaignId = campaign7.id,
                        startedMinions = 455,
                        completedMinions = 400,
                        successfulExecutions = 400,
                        failedExecutions = 55,
                        QUEUED
                    )
                )
            ).count()

            campaignScenarioRepository.saveAll(
                listOf(
                    CampaignScenarioEntity(campaignId = campaign7.id, name = "scenario-1", minionsCount = 6272),
                    CampaignScenarioEntity(campaignId = campaign11.id, name = "scenario-2", minionsCount = 12321),
                    CampaignScenarioEntity(campaignId = campaign8.id, name = "scenario-33", minionsCount = 12321)
                )
            ).count()

            // when
            val fetched = campaignRepository.retrieveCampaignDetailByTenantIdAndKeyIn(
                tenant.id,
                campaignKeys = listOf(),
                campaignNamePatterns = listOf(),
                scenarioNamePatterns = listOf("scenario-1", "scenario-2")
            )

            // then
            assertThat(fetched).all {
                hasSize(1)
                containsOnly(
                    CampaignData(
                        name = "campaign 11",
                        zones = emptySet(),
                        result = SUCCESSFUL,
                        executionTime = campaign11.start?.let {
                            campaign11.end?.toEpochMilli()?.minus(it.toEpochMilli())
                                ?: 0
                        }!! / 1000,
                        startedMinions = 280,
                        completedMinions = 87,
                        successfulExecutions = 41,
                        failedExecutions = 65,
                    )
                )
            }
        }

    @Test
    fun `should not retrieve any campaign data when no campaign key, patterns or scenario names is passed in`() =
        testDispatcherProvider.run {
            // given
            val savedUser = userRepository.save(UserEntity(displayName = "dis-user-2", username = "my-user-2"))
            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant-2", "test-tenant-2"))
            val campaign = CampaignEntity(
                key = "campaign-1",
                name = "campaign 1",
                scheduledMinions = 345,
                configurer = savedUser.id,
                tenantId = tenant.id,
                start = Instant.parse("2022-02-25T00:00:00.00Z"),
                result = SUCCESSFUL,
                end = Instant.parse("2022-03-26T23:12:11.21Z")
            )
            val campaign6 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-6",
                    name = "campaign 6",
                    start = Instant.parse("2022-02-20T11:25:30.00Z"),
                    result = SUCCESSFUL,
                )
            )
            val campaign7 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-7",
                    name = "campaign 7",
                    start = Instant.parse("2022-02-25T00:00:30.00Z"),
                    result = FAILED,
                    end = null
                )
            )

            campaignReportRepository.saveAll(
                listOf(
                    CampaignReportEntity(
                        campaignId = campaign6.id,
                        startedMinions = 1000,
                        completedMinions = 990,
                        successfulExecutions = 990,
                        failedExecutions = 10,
                        SUCCESSFUL
                    )
                )
            ).count()

            campaignScenarioRepository.saveAll(
                listOf(
                    CampaignScenarioEntity(campaignId = campaign7.id, name = "scenario-1", minionsCount = 6272),
                )
            ).count()

            // when
            val fetched = campaignRepository.retrieveCampaignDetailByTenantIdAndKeyIn(
                tenant.id,
                campaignKeys = listOf(),
                campaignNamePatterns = listOf(),
                scenarioNamePatterns = listOf()
            )

            // then
            assertThat(fetched).isEmpty()
        }

    @Test
    fun `should not retrieve any campaign data when a different tenant is passed in`() =
        testDispatcherProvider.run {
            // given
            val savedUser = userRepository.save(UserEntity(displayName = "dis-user-2", username = "my-user-2"))
            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant-2", "test-tenant-2"))
            val unknownTenant = tenantRepository.save(TenantEntity(Instant.now(), "unknown-tenant", "unknown Tenant"))
            val campaign = CampaignEntity(
                key = "campaign-1",
                name = "campaign 1",
                scheduledMinions = 345,
                configurer = savedUser.id,
                tenantId = tenant.id,
                start = Instant.parse("2022-02-25T00:00:00.00Z"),
                result = SUCCESSFUL,
                end = Instant.parse("2022-03-26T23:12:11.21Z")
            )
            val campaign6 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-6",
                    name = "campaign 6",
                    start = Instant.parse("2022-02-20T11:25:30.00Z"),
                    result = SUCCESSFUL,
                )
            )
            val campaign7 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-7",
                    name = "campaign 7",
                    start = Instant.parse("2022-02-25T00:00:30.00Z"),
                    result = FAILED,
                )
            )

            campaignReportRepository.saveAll(
                listOf(
                    CampaignReportEntity(
                        campaignId = campaign6.id,
                        startedMinions = 1000,
                        completedMinions = 990,
                        successfulExecutions = 990,
                        failedExecutions = 10,
                        SUCCESSFUL
                    )
                )
            ).count()

            campaignScenarioRepository.saveAll(
                listOf(
                    CampaignScenarioEntity(campaignId = campaign7.id, name = "scenario-1", minionsCount = 6272),
                )
            ).count()

            // when
            val fetched = campaignRepository.retrieveCampaignDetailByTenantIdAndKeyIn(
                unknownTenant.id,
                campaignKeys = listOf("campaign-6"),
                campaignNamePatterns = listOf(),
                scenarioNamePatterns = listOf("scenario-1")
            )

            // then
            assertThat(fetched).isEmpty()
        }

    @Test
    fun `should retrieve the campaign details data that match the given scenario name pattern`() =
        testDispatcherProvider.run {
            // given
            val savedUser = userRepository.save(UserEntity(displayName = "dis-user-2", username = "my-user-2"))
            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant-2", "test-tenant-2"))
            val campaign = CampaignEntity(
                key = "campaign-1",
                name = "campaign 1",
                scheduledMinions = 345,
                configurer = savedUser.id,
                tenantId = tenant.id,
                start = Instant.parse("2022-02-25T00:00:00.00Z"),
                result = SUCCESSFUL,
                end = Instant.parse("2022-03-26T23:12:11.21Z")
            )
            val campaign6 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-6",
                    name = "campaign 6",
                    start = Instant.parse("2022-02-20T11:25:30.00Z"),
                    result = SUCCESSFUL,
                )
            )
            val campaign8 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-8",
                    name = "campaign 8",
                    start = Instant.parse("2022-02-20T11:25:30.00Z"),
                    result = SUCCESSFUL,
                )
            )
            val campaign11 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-11",
                    name = "campaign 11",
                    start = Instant.parse("2022-02-20T11:25:30.00Z"),
                    result = SUCCESSFUL,
                )
            )
            val campaign7 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-7",
                    name = "campaign 7",
                    start = Instant.parse("2022-02-25T00:00:30.00Z"),
                    result = FAILED,
                    end = null
                )
            )

            campaignRepository.saveAll(
                listOf(
                    campaign,
                    campaign.copy(
                        key = "campaign-17",
                        name = "campaign 17",
                        start = Instant.parse("2022-02-22T01:01:01.00Z"),
                        result = WARNING
                    ),
                    campaign.copy(
                        key = "campaign-5",
                        name = "campaign 5",
                        start = Instant.parse("2022-02-25T00:00:30.00Z"),
                        result = FAILED
                    ),
                )
            ).count()

            campaignReportRepository.saveAll(
                listOf(
                    CampaignReportEntity(
                        campaignId = campaign6.id,
                        startedMinions = 1000,
                        completedMinions = 990,
                        successfulExecutions = 990,
                        failedExecutions = 10,
                        SUCCESSFUL
                    ),
                    CampaignReportEntity(
                        campaignId = campaign8.id,
                        startedMinions = 300,
                        completedMinions = 110,
                        successfulExecutions = 56,
                        failedExecutions = 20,
                        ABORTED
                    ),
                    CampaignReportEntity(
                        campaignId = campaign11.id,
                        startedMinions = 280,
                        completedMinions = 87,
                        successfulExecutions = 41,
                        failedExecutions = 65,
                        WARNING
                    ),
                    CampaignReportEntity(
                        campaignId = campaign7.id,
                        startedMinions = 455,
                        completedMinions = 400,
                        successfulExecutions = 400,
                        failedExecutions = 55,
                        QUEUED
                    )
                )
            ).count()

            campaignScenarioRepository.saveAll(
                listOf(
                    CampaignScenarioEntity(campaignId = campaign7.id, name = "scenario-1", minionsCount = 6272),
                    CampaignScenarioEntity(campaignId = campaign11.id, name = "scenario-2", minionsCount = 12321),
                    CampaignScenarioEntity(campaignId = campaign8.id, name = "scenario-33", minionsCount = 12321)
                )
            ).count()

            // when
            val fetched = campaignRepository.retrieveCampaignDetailByTenantIdAndKeyIn(
                tenant.id,
                campaignKeys = listOf(),
                campaignNamePatterns = listOf(),
                scenarioNamePatterns = listOf("scenario-1", "scenario-2")
            )

            // then
            assertThat(fetched).all {
                hasSize(1)
                containsOnly(
                    CampaignData(
                        name = "campaign 11",
                        zones = emptySet(),
                        result = SUCCESSFUL,
                        executionTime = campaign11.start?.let {
                            campaign11.end?.toEpochMilli()?.minus(it.toEpochMilli())
                                ?: 0
                        }!! / 1000,
                        startedMinions = 280,
                        completedMinions = 87,
                        successfulExecutions = 41,
                        failedExecutions = 65,
                    )
                )
            }
        }

    @Test
    fun `should retrieve all campaign details data that match the given campaign keys, patterns or scenario names in`() =
        testDispatcherProvider.run {
            // given
            val savedUser = userRepository.save(UserEntity(displayName = "dis-user-2", username = "my-user-2"))
            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant-2", "test-tenant-2"))
            val campaign = CampaignEntity(
                key = "campaign-1",
                name = "campaign 1",
                scheduledMinions = 345,
                configurer = savedUser.id,
                tenantId = tenant.id,
                start = Instant.parse("2022-02-25T00:00:00.00Z"),
                result = SUCCESSFUL,
                end = Instant.parse("2022-03-26T23:12:11.21Z")
            )
            val campaign6 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-6",
                    name = "campaign 6",
                    start = Instant.parse("2022-02-20T11:25:30.00Z"),
                    result = SUCCESSFUL,
                )
            )
            val campaign8 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-8",
                    name = "campaign 8",
                    start = Instant.parse("2022-02-20T11:25:30.00Z"),
                    result = SUCCESSFUL,
                )
            )
            val campaign11 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-11",
                    name = "campaign 11",
                    start = Instant.parse("2022-02-20T11:25:30.00Z"),
                    result = SUCCESSFUL,
                )
            )
            val campaign7 = campaignRepository.save(
                campaign.copy(
                    key = "campaign-7",
                    name = "campaign 7",
                    start = Instant.parse("2022-02-25T00:00:30.00Z"),
                    result = FAILED,
                    end = null
                )
            )

            campaignRepository.saveAll(
                listOf(
                    campaign,
                    campaign.copy(
                        key = "campaign-17",
                        name = "campaign 17",
                        start = Instant.parse("2022-02-22T01:01:01.00Z"),
                        result = WARNING
                    ),
                    campaign.copy(
                        key = "campaign-5",
                        name = "campaign 5",
                        start = Instant.parse("2022-02-25T00:00:30.00Z"),
                        result = FAILED
                    ),
                )
            ).count()

            campaignReportRepository.saveAll(
                listOf(
                    CampaignReportEntity(
                        campaignId = campaign6.id,
                        startedMinions = 1000,
                        completedMinions = 990,
                        successfulExecutions = 990,
                        failedExecutions = 10,
                        SUCCESSFUL
                    ),
                    CampaignReportEntity(
                        campaignId = campaign8.id,
                        startedMinions = 300,
                        completedMinions = 110,
                        successfulExecutions = 56,
                        failedExecutions = 20,
                        ABORTED
                    ),
                    CampaignReportEntity(
                        campaignId = campaign11.id,
                        startedMinions = 280,
                        completedMinions = 87,
                        successfulExecutions = 41,
                        failedExecutions = 65,
                        WARNING
                    ),
                    CampaignReportEntity(
                        campaignId = campaign7.id,
                        startedMinions = 455,
                        completedMinions = 400,
                        successfulExecutions = 400,
                        failedExecutions = 55,
                        QUEUED
                    )
                )
            ).count()

            campaignScenarioRepository.saveAll(
                listOf(
                    CampaignScenarioEntity(campaignId = campaign11.id, name = "scenario-2", minionsCount = 12321),
                )
            ).count()

            // when
            val fetched = campaignRepository.retrieveCampaignDetailByTenantIdAndKeyIn(
                tenant.id,
                campaignKeys = listOf("campaign-8", "campaign-1"),
                campaignNamePatterns = listOf("campaign 6", "campaign 7"),
                scenarioNamePatterns = listOf("scenario-2")
            )

            // then
            assertThat(fetched).all {
                hasSize(3)
                containsExactlyInAnyOrder(
                    CampaignData(
                        name = "campaign 8",
                        zones = emptySet(),
                        result = SUCCESSFUL,
                        executionTime = campaign8.start?.let {
                            campaign8.end?.toEpochMilli()?.minus(it.toEpochMilli())
                                ?: 0
                        }!! / 1000,
                        startedMinions = 300,
                        completedMinions = 110,
                        successfulExecutions = 56,
                        failedExecutions = 20,
                    ),
                    CampaignData(
                        name = "campaign 6",
                        zones = emptySet(),
                        result = SUCCESSFUL,
                        executionTime = campaign6.start?.let {
                            campaign6.end?.toEpochMilli()?.minus(it.toEpochMilli())
                                ?: 0
                        }!! / 1000,
                        startedMinions = 1000,
                        completedMinions = 990,
                        successfulExecutions = 990,
                        failedExecutions = 10,
                    ),
                    CampaignData(
                        name = "campaign 11",
                        zones = emptySet(),
                        result = SUCCESSFUL,
                        executionTime = campaign11.start?.let {
                            campaign11.end?.toEpochMilli()?.minus(it.toEpochMilli())
                                ?: 0
                        }!! / 1000,
                        startedMinions = 280,
                        completedMinions = 87,
                        successfulExecutions = 41,
                        failedExecutions = 65,
                    )
                )
            }
        }

    @Test
    fun `should retrieve a campaign from a its key`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
        val tenant2 = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant-2", "test-tenant-2"))
        val saved1 = campaignRepository.save(
            campaignPrototype.copy(
                key = "key-1",
                tenantId = tenant.id,
                failureReason = "First failure",
                zones = setOf("at", "fr")
            )
        )
        val saved6 = campaignRepository.save(
            campaignPrototype.copy(
                key = "key-6",
                tenantId = tenant2.id,
                failureReason = "Third failure 3",
                zones = setOf("at", "fr")
            )
        )
        assertThat(campaignRepository.findAll().count()).isEqualTo(2)


        // when + then
        assertThat(campaignRepository.findByTenantAndKey("my-tenant", "key-1")).isNotNull()
            .all {
                isDataClassEqualTo(saved1)
            }

        assertThat(campaignRepository.findByTenantAndKey("my-tenant-2", "key-6")).isNotNull()
            .all {
                isDataClassEqualTo(saved6)
            }

        assertThat(campaignRepository.findByTenantAndKey("my-tenant-2", "key-2")).isNull()
    }

    @Test
    fun `should retrieve a collection of campaign from a set of keys`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
        val tenant2 = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant-2", "test-tenant-2"))
        val saved1 = campaignRepository.save(
            campaignPrototype.copy(
                key = "key-1",
                tenantId = tenant.id,
                failureReason = "First failure",
                zones = setOf("at", "fr")
            )
        )
        val saved2 = campaignRepository.save(
            campaignPrototype.copy(
                key = "key-2",
                tenantId = tenant.id,
                failureReason = "Second failure",
                zones = setOf("at", "fr")
            )
        )

        val saved3 = campaignRepository.save(
            campaignPrototype.copy(
                key = "key-3",
                tenantId = tenant.id,
                failureReason = "Third failure",
                zones = setOf("at", "fr")
            )
        )
        val saved4 = campaignRepository.save(
            campaignPrototype.copy(
                key = "key-4",
                tenantId = tenant.id,
                failureReason = "Fourth failure",
                zones = setOf("at", "fr")
            )
        )
        val saved5 = campaignRepository.save(
            campaignPrototype.copy(
                key = "key-5",
                tenantId = tenant2.id,
                failureReason = "Fourth failure 2",
                zones = setOf("at", "fr")
            )
        )
        val saved6 = campaignRepository.save(
            campaignPrototype.copy(
                key = "key-6",
                tenantId = tenant2.id,
                failureReason = "Third failure 3",
                zones = setOf("at", "fr")
            )
        )
        assertThat(campaignRepository.findAll().count()).isEqualTo(6)


        // when + then
        assertThat(campaignRepository.findByTenantAndKeys("my-tenant", listOf("key-1", "key-3", "key-2"))).isNotNull()
            .all {
                hasSize(3)
                containsExactlyInAnyOrder(saved1, saved2, saved3)
            }

        assertThat(campaignRepository.findByTenantAndKeys("my-tenant", listOf("key-5", "key-6", "key-4"))).isNotNull()
            .all {
                hasSize(1)
                isEqualTo(listOf(saved4))
            }

        assertThat(campaignRepository.findByTenantAndKeys("my-tenant-2", listOf("key-5", "key-6"))).isNotNull()
            .all {
                hasSize(2)
                isEqualTo(listOf(saved5, saved6))
            }

        assertThat(campaignRepository.findByTenantAndKeys("my-tenant-2", listOf("key-1", "key-3"))).isEmpty()
    }

    @Test
    fun `should retrieve a campaign ID from a its key`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
        val tenant2 = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant-2", "test-tenant-2"))
        val saved1 = campaignRepository.save(
            campaignPrototype.copy(
                key = "key-1",
                tenantId = tenant.id,
                failureReason = "First failure",
                zones = setOf("at", "fr")
            )
        )
        val saved6 = campaignRepository.save(
            campaignPrototype.copy(
                key = "key-6",
                tenantId = tenant2.id,
                failureReason = "Third failure 3",
                zones = setOf("at", "fr")
            )
        )
        assertThat(campaignRepository.findAll().count()).isEqualTo(2)


        // when + then
        assertThat(campaignRepository.findIdByTenantAndKey("my-tenant", "key-1")).isNotNull().isEqualTo(saved1.id)

        assertThat(campaignRepository.findIdByTenantAndKey("my-tenant-2", "key-6")).isNotNull().isEqualTo(saved6.id)

        assertThat(campaignRepository.findIdByTenantAndKey("my-tenant-2", "key-2")).isNull()
    }

    @Test
    fun `should retrieve a scheduled campaign by its tenant and key`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
        val saved1 = campaignRepository.save(
            campaignPrototype.copy(
                key = "key-1",
                tenantId = tenant.id,
                zones = setOf("at", "fr"),
                result = SCHEDULED
            )
        )
        campaignRepository.save(
            campaignPrototype.copy(
                key = "key-13",
                tenantId = tenant.id,
                zones = setOf("at", "fr"),
                result = SCHEDULED
            )
        )
        assertThat(campaignRepository.findAll().count()).isEqualTo(2)

        // when + then
        assertThat(campaignRepository.findByTenantAndKeyAndScheduled("my-tenant", "key-1"))
            .isNotNull()
            .isEqualTo(saved1)
    }

    @Test
    fun `should retrieve no campaign if the status is not scheduled`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
        campaignRepository.save(
            campaignPrototype.copy(
                key = "key-1",
                tenantId = tenant.id,
                zones = setOf("at", "fr"),
            )
        )
        assertThat(campaignRepository.findAll().count()).isEqualTo(1)

        // when + then
        assertThat(campaignRepository.findByTenantAndKeyAndScheduled("my-tenant", "key-1"))
            .isNull()
    }

    @Test
    fun `should retrieve a fully populated campaign entity completely by its key`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
        campaignRepository.save(
            campaignPrototype.copy(
                key = "key-1",
                tenantId = tenant.id,
                failureReason = "First failure",
                zones = setOf("at", "fr"),
                name = "This is my new campaign",
                speedFactor = 123.0,
                start = Instant.now() - Duration.ofSeconds(173),
                end = Instant.now(),
                scheduledMinions = 123,
                result = SUCCESSFUL,
                configurer = 1L, // Default user.
                configuration = CampaignConfiguration(
                    name = "This Campaign config",
                    speedFactor = 1.0,
                    startOffsetMs = 1000,
                    scenarios = mapOf("sce1" to ScenarioRequest(minionsCount = 1)),
                    scheduling = DailyScheduling("Asia/Taipei", setOf(3, 5))
                )
            )
        )
        assertThat(campaignRepository.findAll().count()).isEqualTo(1)

        // when + then
        assertThat(campaignRepository.findByTenantAndKey("my-tenant", "key-1")).isNotNull()
            .all {
                prop(CampaignEntity::name).isEqualTo("This is my new campaign")
                prop(CampaignEntity::key).isEqualTo("key-1")
                prop(CampaignEntity::configuration).isNotNull().all {
                    prop(CampaignConfiguration::name).isEqualTo("This Campaign config")
                    prop(CampaignConfiguration::speedFactor).isEqualTo(1.0)
                    prop(CampaignConfiguration::startOffsetMs).isEqualTo(1000)
                    prop(CampaignConfiguration::scenarios).isEqualTo(mapOf("sce1" to ScenarioRequest(minionsCount = 1)))
                    prop(CampaignConfiguration::scheduling).isNotNull().isInstanceOf(DailyScheduling::class).all {
                        prop(DailyScheduling::timeZone).isEqualTo("Asia/Taipei")
                        prop(DailyScheduling::restrictions).isEqualTo(setOf(3, 5))
                    }
                }
            }
    }
}