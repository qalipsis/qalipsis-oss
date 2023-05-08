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

package io.qalipsis.core.head.campaign

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ExecutionStatus.QUEUED
import io.qalipsis.api.report.ExecutionStatus.SCHEDULED
import io.qalipsis.api.sync.Latch
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.head.campaign.scheduler.CampaignScheduler
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.hook.CampaignHook
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.FactoryRepository
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.core.head.model.converter.CampaignConfigurationConverter
import io.qalipsis.core.head.model.converter.CampaignConverter
import io.qalipsis.core.head.web.handler.BulkIllegalArgumentException
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.BeforeAll

@WithMockk
internal class PersistentCampaignServiceTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var campaignRepository: CampaignRepository

    @RelaxedMockK
    private lateinit var campaignScenarioRepository: CampaignScenarioRepository

    @RelaxedMockK
    private lateinit var userRepository: UserRepository

    @RelaxedMockK
    private lateinit var tenantRepository: TenantRepository

    @RelaxedMockK
    private lateinit var campaignConfigurationConverter: CampaignConfigurationConverter

    @RelaxedMockK
    private lateinit var campaignConverter: CampaignConverter

    @RelaxedMockK
    private lateinit var factoryRepository: FactoryRepository

    @RelaxedMockK
    private lateinit var campaignScheduler: CampaignScheduler

    @RelaxedMockK
    private lateinit var hook1: CampaignHook

    @RelaxedMockK
    private lateinit var hook2: CampaignHook

    @MockK
    lateinit var factoryService: FactoryService

    private lateinit var persistentCampaignService: PersistentCampaignService

    @BeforeAll
    internal fun setUp() {
        persistentCampaignService = spyk(
            PersistentCampaignService(
                campaignRepository = campaignRepository,
                userRepository = userRepository,
                tenantRepository = tenantRepository,
                campaignScenarioRepository = campaignScenarioRepository,
                campaignConfigurationConverter = campaignConfigurationConverter,
                campaignConverter = campaignConverter,
                factoryRepository = factoryRepository,
                campaignScheduler = campaignScheduler,
                factoryService = factoryService,
                hooks = listOf(hook1, hook2)
            ),
            recordPrivateCalls = true
        )
    }

    @Test
    internal fun `should create the new campaign`() = testDispatcherProvider.run {
        // given
        val campaign = CampaignConfiguration(
            name = "This is a campaign",
            speedFactor = 123.2,
            scenarios = mapOf(
                "scenario-1" to ScenarioRequest(1),
                "scenario-2" to ScenarioRequest(3)
            )
        )
        val runningCampaign = relaxedMockk<RunningCampaign> {
            every { key } returns "my-campaign"
            every { scenarios } returns mapOf(
                "scenario-1" to relaxedMockk { every { minionsCount } returns 6272 },
                "scenario-2" to relaxedMockk { every { minionsCount } returns 12321 }
            )
        }
        coEvery {
            persistentCampaignService["convertAndSaveCampaign"](
                refEq("my-tenant"),
                refEq("my-user"),
                refEq(campaign),
                refEq(false)
            )
        } returns runningCampaign

        // when
        val result = persistentCampaignService.create("my-tenant", "my-user", campaign)

        // then
        assertThat(result).isSameAs(runningCampaign)
        coVerifyOrder {
            persistentCampaignService["convertAndSaveCampaign"](
                refEq("my-tenant"),
                refEq("my-user"),
                refEq(campaign),
                refEq(false)
            )
        }

        confirmVerified(
            userRepository,
            campaignRepository,
            tenantRepository,
            campaignScenarioRepository,
            campaignConfigurationConverter,
            campaignConverter,
            factoryRepository,
            campaignScheduler,
            factoryService,
            hook1,
            hook2
        )
    }

    @Test
    internal fun `should prepare the campaign`() = testDispatcherProvider.run {
        // given
        coJustRun { campaignRepository.prepare(any(), any()) }

        // when
        persistentCampaignService.prepare("my-tenant", "my-campaign")

        // then
        coVerifyOnce {
            campaignRepository.prepare("my-tenant", "my-campaign")
        }

        confirmVerified(
            userRepository,
            campaignRepository,
            tenantRepository,
            campaignScenarioRepository,
            campaignConfigurationConverter,
            campaignConverter,
            factoryRepository,
            campaignScheduler,
            factoryService,
            hook1,
            hook2
        )
    }

    @Test
    internal fun `should start the campaign`() = testDispatcherProvider.run {
        // given
        val start = Instant.now()
        val timeout = Instant.now().plusSeconds(243)
        coJustRun { campaignRepository.start("my-tenant", "my-campaign", start, timeout, null) }

        // when
        persistentCampaignService.start("my-tenant", "my-campaign", start, timeout, null)

        // then
        coVerifyOnce {
            campaignRepository.start("my-tenant", "my-campaign", start, timeout, null)
        }

        confirmVerified(
            userRepository,
            campaignRepository,
            tenantRepository,
            campaignScenarioRepository,
            campaignConfigurationConverter,
            campaignConverter,
            factoryRepository,
            campaignScheduler,
            factoryService,
            hook1,
            hook2
        )
    }

    @Test
    internal fun `should close the running campaign`() = testDispatcherProvider.run {
        // given
        val campaignEntity = relaxedMockk<CampaignEntity>()
        val convertedCampaign = relaxedMockk<Campaign>()
        coEvery { campaignConverter.convertToModel(refEq(campaignEntity)) } returns convertedCampaign
        coEvery { campaignRepository.findByTenantAndKey("my-tenant", "my-campaign") } returns campaignEntity

        // when
        val result =
            persistentCampaignService.close("my-tenant", "my-campaign", ExecutionStatus.FAILED, "This is the failure")

        // then
        assertThat(result).isSameAs(convertedCampaign)
        coVerifyOnce {
            campaignRepository.complete("my-tenant", "my-campaign", ExecutionStatus.FAILED, "This is the failure")
            campaignRepository.findByTenantAndKey("my-tenant", "my-campaign")
            campaignConverter.convertToModel(refEq(campaignEntity))
        }

        confirmVerified(
            userRepository,
            campaignRepository,
            tenantRepository,
            campaignScenarioRepository,
            campaignConfigurationConverter,
            campaignConverter,
            factoryRepository,
            campaignScheduler,
            factoryService,
            hook1,
            hook2
        )
    }

    @Test
    internal fun `should returns the searched campaigns from the repository with default sorting`() =
        testDispatcherProvider.run {
            // given
            val campaignEntity1 = relaxedMockk<CampaignEntity>()
            val campaignEntity2 = relaxedMockk<CampaignEntity>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.desc("start")))
            val page = Page.of(listOf(campaignEntity1, campaignEntity2), pageable, 2)

            val campaign1 = relaxedMockk<Campaign>()
            val campaign2 = relaxedMockk<Campaign>()
            coEvery { campaignRepository.findAll("my-tenant", pageable) } returns page
            coEvery { campaignConverter.convertToModel(any()) } returns campaign1 andThen campaign2

            // when
            val result = persistentCampaignService.search("my-tenant", emptyList(), null, 0, 20)

            // then
            assertThat(result).all {
                prop(io.qalipsis.api.query.Page<Campaign>::page).isEqualTo(0)
                prop(io.qalipsis.api.query.Page<Campaign>::totalPages).isEqualTo(1)
                prop(io.qalipsis.api.query.Page<Campaign>::totalElements).isEqualTo(2)
                prop(io.qalipsis.api.query.Page<Campaign>::elements).all {
                    hasSize(2)
                    containsExactly(campaign1, campaign2)
                }
            }
            coVerifyOrder {
                campaignRepository.findAll("my-tenant", pageable)
                campaignConverter.convertToModel(refEq(campaignEntity1))
                campaignConverter.convertToModel(refEq(campaignEntity2))
            }

            confirmVerified(
                userRepository,
                campaignRepository,
                tenantRepository,
                campaignScenarioRepository,
                campaignConfigurationConverter,
                campaignConverter,
                factoryRepository,
                campaignScheduler,
                factoryService,
                hook1,
                hook2
            )
        }

    @Test
    internal fun `should returns the searched campaigns from the repository with sorting asc`() =
        testDispatcherProvider.run {
            // given
            val campaignEntity1 = relaxedMockk<CampaignEntity>()
            val campaignEntity2 = relaxedMockk<CampaignEntity>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.asc("name")))
            val page = Page.of(listOf(campaignEntity1, campaignEntity2), Pageable.from(0, 20), 2)

            val campaign1 = relaxedMockk<Campaign>()
            val campaign2 = relaxedMockk<Campaign>()
            coEvery { campaignRepository.findAll("my-tenant", pageable) } returns page
            coEvery { campaignConverter.convertToModel(any()) } returns campaign1 andThen campaign2

            // when
            val result = persistentCampaignService.search("my-tenant", emptyList(), "name:asc", 0, 20)

            // then
            assertThat(result).all {
                prop(io.qalipsis.api.query.Page<Campaign>::page).isEqualTo(0)
                prop(io.qalipsis.api.query.Page<Campaign>::totalPages).isEqualTo(1)
                prop(io.qalipsis.api.query.Page<Campaign>::totalElements).isEqualTo(2)
                prop(io.qalipsis.api.query.Page<Campaign>::elements).all {
                    hasSize(2)
                    containsExactly(campaign1, campaign2)
                }
            }
            coVerifyOrder {
                campaignRepository.findAll("my-tenant", pageable)
                campaignConverter.convertToModel(refEq(campaignEntity1))
                campaignConverter.convertToModel(refEq(campaignEntity2))
            }

            confirmVerified(
                userRepository,
                campaignRepository,
                tenantRepository,
                campaignScenarioRepository,
                campaignConfigurationConverter,
                campaignConverter,
                factoryRepository,
                campaignScheduler,
                factoryService,
                hook1,
                hook2
            )
        }

    @Test
    internal fun `should returns the searched campaigns from the repository with sorting desc`() =
        testDispatcherProvider.run {
            // given
            val campaignEntity1 = relaxedMockk<CampaignEntity>()
            val campaignEntity2 = relaxedMockk<CampaignEntity>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.desc("name")))
            val page = Page.of(listOf(campaignEntity1, campaignEntity2), Pageable.from(0, 20), 2)

            val campaign1 = relaxedMockk<Campaign>()
            val campaign2 = relaxedMockk<Campaign>()
            coEvery { campaignRepository.findAll("my-tenant", pageable) } returns page
            coEvery { campaignConverter.convertToModel(any()) } returns campaign1 andThen campaign2

            // when
            val result = persistentCampaignService.search("my-tenant", emptyList(), "name:desc", 0, 20)

            // then
            assertThat(result).all {
                prop(io.qalipsis.api.query.Page<Campaign>::page).isEqualTo(0)
                prop(io.qalipsis.api.query.Page<Campaign>::totalPages).isEqualTo(1)
                prop(io.qalipsis.api.query.Page<Campaign>::totalElements).isEqualTo(2)
                prop(io.qalipsis.api.query.Page<Campaign>::elements).all {
                    hasSize(2)
                    containsExactly(campaign1, campaign2)
                }
            }
            coVerifyOrder {
                campaignRepository.findAll("my-tenant", pageable)
                campaignConverter.convertToModel(refEq(campaignEntity1))
                campaignConverter.convertToModel(refEq(campaignEntity2))
            }

            confirmVerified(
                userRepository,
                campaignRepository,
                tenantRepository,
                campaignScenarioRepository,
                campaignConfigurationConverter,
                campaignConverter,
                factoryRepository,
                campaignScheduler,
                factoryService,
                hook1,
                hook2
            )
        }

    @Test
    internal fun `should returns the searched campaigns from the repository with sorting`() =
        testDispatcherProvider.run {
            // given
            val campaignEntity1 = relaxedMockk<CampaignEntity>()
            val campaignEntity2 = relaxedMockk<CampaignEntity>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.asc("name")))
            val page = Page.of(listOf(campaignEntity1, campaignEntity2), Pageable.from(0, 20), 2)

            val campaign1 = relaxedMockk<Campaign>()
            val campaign2 = relaxedMockk<Campaign>()
            coEvery { campaignRepository.findAll("my-tenant", pageable) } returns page
            coEvery { campaignConverter.convertToModel(any()) } returns campaign1 andThen campaign2

            // when
            val result = persistentCampaignService.search("my-tenant", emptyList(), "name", 0, 20)

            // then
            assertThat(result).all {
                prop(io.qalipsis.api.query.Page<Campaign>::page).isEqualTo(0)
                prop(io.qalipsis.api.query.Page<Campaign>::totalPages).isEqualTo(1)
                prop(io.qalipsis.api.query.Page<Campaign>::totalElements).isEqualTo(2)
                prop(io.qalipsis.api.query.Page<Campaign>::elements).all {
                    hasSize(2)
                    containsExactly(campaign1, campaign2)
                }
            }
            coVerifyOrder {
                campaignRepository.findAll("my-tenant", pageable)
                campaignConverter.convertToModel(refEq(campaignEntity1))
                campaignConverter.convertToModel(refEq(campaignEntity2))
            }

            confirmVerified(
                userRepository,
                campaignRepository,
                tenantRepository,
                campaignScenarioRepository,
                campaignConfigurationConverter,
                campaignConverter,
                factoryRepository,
                campaignScheduler,
                factoryService,
                hook1,
                hook2
            )
        }

    @Test
    internal fun `should returns the searched campaigns from the repository with sorting and filtering`() =
        testDispatcherProvider.run {
            // given
            val campaignEntity1 = relaxedMockk<CampaignEntity>()
            val campaignEntity2 = relaxedMockk<CampaignEntity>()
            val filter1 = "%test%"
            val filter2 = "%he%lo%"
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.asc("name")))
            val page = Page.of(listOf(campaignEntity1, campaignEntity2), Pageable.from(0, 20), 2)
            coEvery { campaignRepository.findAll("my-tenant", listOf(filter1, filter2), pageable) } returns page

            val campaign1 = relaxedMockk<Campaign>()
            val campaign2 = relaxedMockk<Campaign>()
            coEvery { campaignConverter.convertToModel(any()) } returns campaign1 andThen campaign2

            // when
            val result = persistentCampaignService.search("my-tenant", listOf("test", "he*lo"), "name", 0, 20)

            // then
            assertThat(result).all {
                prop(io.qalipsis.api.query.Page<Campaign>::page).isEqualTo(0)
                prop(io.qalipsis.api.query.Page<Campaign>::totalPages).isEqualTo(1)
                prop(io.qalipsis.api.query.Page<Campaign>::totalElements).isEqualTo(2)
                prop(io.qalipsis.api.query.Page<Campaign>::elements).all {
                    hasSize(2)
                    containsExactly(campaign1, campaign2)
                }
            }
            coVerifyOrder {
                campaignRepository.findAll("my-tenant", listOf(filter1, filter2), pageable)
                campaignConverter.convertToModel(refEq(campaignEntity1))
                campaignConverter.convertToModel(refEq(campaignEntity2))
            }

            confirmVerified(
                userRepository,
                campaignRepository,
                tenantRepository,
                campaignScenarioRepository,
                campaignConfigurationConverter,
                campaignConverter,
                factoryRepository,
                campaignScheduler,
                factoryService,
                hook1,
                hook2
            )
        }

    @Test
    internal fun `should save the aborter to the campaign`() = testDispatcherProvider.run {
        val now = Instant.now()
        val campaign = CampaignEntity(
            key = "my-campaign",
            name = "This is a campaign",
            speedFactor = 123.2,
            scheduledMinions = 345,
            start = now,
            configurer = 199
        )
        coEvery { campaignRepository.findByTenantAndKey("my-tenant", "my-campaign") } returns campaign
        coEvery { userRepository.findIdByUsername("my-aborter") } returns 111
        coEvery { campaignRepository.update(any()) } returnsArgument 0

        // when
        persistentCampaignService.abort("my-tenant", "my-aborter", "my-campaign")

        // then
        val capturedEntity = mutableListOf<CampaignEntity>()
        coVerifyOnce {
            campaignRepository.findByTenantAndKey("my-tenant", "my-campaign")
            userRepository.findIdByUsername("my-aborter")
            campaignRepository.update(capture(capturedEntity))
        }
        assertThat(capturedEntity).all {
            hasSize(1)
            any {
                it.isInstanceOf(CampaignEntity::class).all {
                    prop(CampaignEntity::key).isEqualTo("my-campaign")
                    prop(CampaignEntity::name).isEqualTo("This is a campaign")
                    prop(CampaignEntity::scheduledMinions).isEqualTo(345)
                    prop(CampaignEntity::speedFactor).isEqualTo(123.2)
                    prop(CampaignEntity::configurer).isEqualTo(199)
                    prop(CampaignEntity::aborter).isEqualTo(111)
                }
            }
        }

        confirmVerified(
            userRepository,
            campaignRepository,
            tenantRepository,
            campaignScenarioRepository,
            campaignConfigurationConverter,
            campaignConverter,
            factoryRepository,
            campaignScheduler,
            factoryService,
            hook1,
            hook2
        )
    }

    @Test
    internal fun `should enrich the campaign without failure message`() = testDispatcherProvider.run {
        // given
        val now = Instant.now()
        val campaign = CampaignEntity(
            key = "my-campaign",
            name = "This is a campaign",
            speedFactor = 123.2,
            scheduledMinions = 345,
            start = now,
            configurer = 199
        )
        coEvery { campaignRepository.findByTenantAndKey("my-tenant", "my-campaign") } returns campaign
        coEvery { campaignRepository.update(any()) } returnsArgument 0
        coEvery {
            factoryRepository.findByNodeIdIn(
                "my-tenant",
                setOf("factory-1", "factory-2", "factory-3")
            )
        } returns listOf(
            mockk { every { zone } returns "zone-1" },
            mockk { every { zone } returns null },
            mockk { every { zone } returns "zone-3" }
        )

        // when
        persistentCampaignService.enrich(mockk {
            every { tenant } returns "my-tenant"
            every { key } returns "my-campaign"
            every { message } returns " "
            every { factories.keys } returns mutableSetOf("factory-1", "factory-2", "factory-3")
        })

        // then
        coVerifyOnce {
            campaignRepository.findByTenantAndKey("my-tenant", "my-campaign")
            factoryRepository.findByNodeIdIn("my-tenant", setOf("factory-1", "factory-2", "factory-3"))
            campaignRepository.update(
                eq(
                    CampaignEntity(
                        key = "my-campaign",
                        name = "This is a campaign",
                        speedFactor = 123.2,
                        scheduledMinions = 345,
                        start = now,
                        configurer = 199,
                        failureReason = null,
                        zones = setOf("zone-1", "zone-3")
                    ).copy(
                        creation = campaign.creation,
                        version = campaign.version
                    )
                )
            )
        }

        confirmVerified(
            userRepository,
            campaignRepository,
            tenantRepository,
            campaignScenarioRepository,
            campaignConfigurationConverter,
            campaignConverter,
            factoryRepository,
            campaignScheduler,
            factoryService,
            hook1,
            hook2
        )
    }


    @Test
    internal fun `should enrich the campaign with failure message`() = testDispatcherProvider.run {
        // given
        val now = Instant.now()
        val campaign = CampaignEntity(
            key = "my-campaign",
            name = "This is a campaign",
            speedFactor = 123.2,
            scheduledMinions = 345,
            start = now,
            configurer = 199
        )
        coEvery { campaignRepository.findByTenantAndKey("my-tenant", "my-campaign") } returns campaign
        coEvery { campaignRepository.update(any()) } returnsArgument 0
        coEvery {
            factoryRepository.findByNodeIdIn(
                "my-tenant",
                setOf("factory-1", "factory-2", "factory-3")
            )
        } returns listOf(
            mockk { every { zone } returns "zone-1" },
            mockk { every { zone } returns null },
            mockk { every { zone } returns "zone-3" }
        )

        // when
        persistentCampaignService.enrich(mockk {
            every { tenant } returns "my-tenant"
            every { key } returns "my-campaign"
            every { message } returns "The failure"
            every { factories.keys } returns mutableSetOf("factory-1", "factory-2", "factory-3")
        })

        // then
        coVerifyOnce {
            campaignRepository.findByTenantAndKey("my-tenant", "my-campaign")
            factoryRepository.findByNodeIdIn("my-tenant", setOf("factory-1", "factory-2", "factory-3"))
            campaignRepository.update(
                eq(
                    CampaignEntity(
                        key = "my-campaign",
                        name = "This is a campaign",
                        speedFactor = 123.2,
                        scheduledMinions = 345,
                        start = now,
                        configurer = 199,
                        failureReason = "The failure",
                        zones = setOf("zone-1", "zone-3")
                    ).copy(
                        creation = campaign.creation,
                        version = campaign.version
                    )
                )
            )
        }

        confirmVerified(
            userRepository,
            campaignRepository,
            tenantRepository,
            campaignScenarioRepository,
            campaignConfigurationConverter,
            campaignConverter,
            factoryRepository,
            campaignScheduler,
            factoryService,
            hook1,
            hook2
        )
    }

    @Test
    internal fun `should retrieve the configuration when it exists`() = testDispatcherProvider.run {
        // given
        val campaignConfiguration = mockk<CampaignConfiguration>()
        coEvery { campaignRepository.findByTenantAndKey("my-tenant", "my-campaign") } returns mockk {
            every { configuration } returns campaignConfiguration
        }

        // when
        val retrievedConfiguration = persistentCampaignService.retrieveConfiguration("my-tenant", "my-campaign")

        // then
        assertThat(retrievedConfiguration).isSameAs(campaignConfiguration)
        coVerifyOnce { campaignRepository.findByTenantAndKey("my-tenant", "my-campaign") }

        confirmVerified(
            userRepository,
            campaignRepository,
            tenantRepository,
            campaignScenarioRepository,
            campaignConfigurationConverter,
            campaignConverter,
            factoryRepository,
            campaignScheduler,
            factoryService,
            hook1,
            hook2
        )
    }

    @Test
    internal fun `should generate a failure when retrieving the configuration of a non-existing campaign`() =
        testDispatcherProvider.run {
            // given
            coEvery { campaignRepository.findByTenantAndKey("my-tenant", "my-campaign") } returns null

            // when
            assertThrows<IllegalArgumentException> {
                persistentCampaignService.retrieveConfiguration("my-tenant", "my-campaign")
            }

            // then
            coVerifyOnce { campaignRepository.findByTenantAndKey("my-tenant", "my-campaign") }

            confirmVerified(
                userRepository,
                campaignRepository,
                tenantRepository,
                campaignScenarioRepository,
                campaignConfigurationConverter,
                campaignConverter,
                factoryRepository,
                campaignScheduler,
                factoryService,
                hook1,
                hook2
            )
        }

    @Test
    internal fun `should generate a failure when retrieving the missing configuration of an existing campaign`() =
        testDispatcherProvider.run {
            // given
            coEvery { campaignRepository.findByTenantAndKey("my-tenant", "my-campaign") } returns mockk {
                every { configuration } returns null
            }

            // when
            assertThrows<IllegalArgumentException> {
                persistentCampaignService.retrieveConfiguration("my-tenant", "my-campaign")
            }

            // then
            coVerifyOnce { campaignRepository.findByTenantAndKey("my-tenant", "my-campaign") }

            confirmVerified(
                userRepository,
                campaignRepository,
                tenantRepository,
                campaignScenarioRepository,
                campaignConfigurationConverter,
                campaignConverter,
                factoryRepository,
                campaignScheduler,
                hook1,
                hook2
            )
        }

    @Test
    internal fun `should schedule a campaign`() = testDispatcherProvider.run {
        // given
        val scheduleAt = Instant.now().plusSeconds(60)
        val configuration = CampaignConfiguration(
            name = "This is a campaign",
            speedFactor = 123.2,
            scenarios = mapOf(
                "scenario-1" to ScenarioRequest(1),
                "scenario-2" to ScenarioRequest(3)
            ),
            scheduledAt = scheduleAt
        )

        val runningCampaign = relaxedMockk<RunningCampaign> {
            every { key } returns "my-campaign"
            every { scenarios } returns mapOf(
                "scenario-1" to relaxedMockk { every { minionsCount } returns 6272 },
                "scenario-2" to relaxedMockk { every { minionsCount } returns 12321 }
            )
        }

        val latch = Latch(true)
        val scenario1 = relaxedMockk<ScenarioSummary> { every { name } returns "scenario-1" }
        val scenario2 = relaxedMockk<ScenarioSummary> { every { name } returns "scenario-2" }
        val scenario3 = relaxedMockk<ScenarioSummary> { every { name } returns "scenario-1" }
        coEvery { factoryService.getActiveScenarios(refEq("my-tenant"), setOf("scenario-1", "scenario-2")) } returns
                listOf(scenario1, scenario2, scenario3)
        coEvery {
            persistentCampaignService["convertAndSaveCampaign"](
                refEq("my-tenant"),
                refEq("my-user"),
                refEq(configuration),
                refEq(true)
            )
        } returns runningCampaign
        coEvery { campaignScheduler.schedule(refEq("my-campaign"), refEq(scheduleAt)) } coAnswers { latch.release() }

        // when
        val result = persistentCampaignService.schedule("my-tenant", "my-user", configuration)
        latch.await()

        // then
        assertThat(result).isSameAs(runningCampaign)
        coVerifyOrder {
            factoryService.getActiveScenarios(refEq("my-tenant"), setOf("scenario-1", "scenario-2"))
            persistentCampaignService["convertAndSaveCampaign"](
                refEq("my-tenant"),
                refEq("my-user"),
                refEq(configuration),
                refEq(true)
            )
            campaignScheduler.schedule(refEq("my-campaign"), refEq(scheduleAt))
        }

        confirmVerified(
            userRepository,
            campaignRepository,
            tenantRepository,
            campaignScenarioRepository,
            campaignConfigurationConverter,
            campaignConverter,
            factoryRepository,
            campaignScheduler,
            factoryService,
            hook1,
            hook2
        )
    }

    @Test
    internal fun `should not schedule a campaign when scheduleAt is null`() = testDispatcherProvider.run {
        // given
        val configuration = CampaignConfiguration(
            name = "This is a campaign",
            speedFactor = 123.2,
            scenarios = mapOf()
        )

        // when
        val exception = assertThrows<IllegalArgumentException> {
            persistentCampaignService.schedule("my-tenant", "my-user", configuration)
        }

        // then
        assertThat(exception.message)
            .isEqualTo("The schedule time should be in the future")

        confirmVerified(
            userRepository,
            campaignRepository,
            tenantRepository,
            campaignScenarioRepository,
            campaignConfigurationConverter,
            campaignConverter,
            factoryRepository,
            campaignScheduler,
            factoryService,
            hook1,
            hook2
        )
    }

    @Test
    internal fun `should not schedule a campaign when scheduleAt is not in the future`() = testDispatcherProvider.run {
        // given
        val configuration = CampaignConfiguration(
            name = "This is a campaign",
            speedFactor = 123.2,
            scenarios = mapOf(),
            scheduledAt = Instant.now()
        )

        // when
        val exception = assertThrows<IllegalArgumentException> {
            persistentCampaignService.schedule("my-tenant", "my-user", configuration)
        }

        // then
        assertThat(exception.message)
            .isEqualTo("The schedule time should be in the future")

        confirmVerified(
            userRepository,
            campaignRepository,
            tenantRepository,
            campaignScenarioRepository,
            campaignConfigurationConverter,
            campaignConverter,
            factoryRepository,
            campaignScheduler,
            factoryService,
            hook1,
            hook2
        )
    }

    @Test
    internal fun `should not schedule a campaign when some scenarios are currently not supported`() =
        testDispatcherProvider.runTest {
            // given
            val scheduleAt = Instant.now().plusSeconds(60)
            val configuration = CampaignConfiguration(
                name = "This is a campaign",
                speedFactor = 123.2,
                scenarios = mapOf(
                    "scenario-1" to ScenarioRequest(1),
                    "scenario-2" to ScenarioRequest(3)
                ),
                scheduledAt = scheduleAt
            )
            val scenario1 = relaxedMockk<ScenarioSummary> { every { name } returns "scenario-1" }
            val scenario3 = relaxedMockk<ScenarioSummary> { every { name } returns "scenario-1" }

            coEvery {
                factoryService.getActiveScenarios(
                    refEq("my-tenant"),
                    setOf("scenario-1", "scenario-2")
                )
            } returns listOf(scenario1, scenario3)

            // when
            val exception = assertThrows<IllegalArgumentException> {
                persistentCampaignService.schedule("my-tenant", "my-user", configuration)
            }

            // then
            assertThat(exception.message)
                .isEqualTo("The scenarios scenario-2 were not found or are not currently supported by healthy factories")
            coVerifyOrder {
                factoryService.getActiveScenarios(refEq("my-tenant"), setOf("scenario-1", "scenario-2"))
            }

            confirmVerified(
                userRepository,
                campaignRepository,
                tenantRepository,
                campaignScenarioRepository,
                campaignConfigurationConverter,
                campaignConverter,
                factoryRepository,
                campaignScheduler,
                factoryService,
                hook1,
                hook2
            )

        }

    @Test
    internal fun `should save a campaign during creation without timeout`() = testDispatcherProvider.run {
        // given
        val campaign = CampaignConfiguration(
            name = "This is a campaign",
            speedFactor = 123.2,
            scenarios = mapOf(
                "scenario-1" to ScenarioRequest(1),
                "scenario-2" to ScenarioRequest(3)
            )
        )
        val runningCampaign = relaxedMockk<RunningCampaign> {
            every { key } returns "my-campaign"
            every { scenarios } returns mapOf(
                "scenario-1" to relaxedMockk { every { minionsCount } returns 6272 },
                "scenario-2" to relaxedMockk { every { minionsCount } returns 12321 }
            )
        }
        val savedEntity = relaxedMockk<CampaignEntity> {
            every { id } returns 8126
        }
        coEvery { tenantRepository.findIdByReference("my-tenant") } returns 8165L
        coEvery {
            campaignConfigurationConverter.convertConfiguration(
                "my-tenant",
                refEq(campaign)
            )
        } returns runningCampaign
        coEvery { campaignRepository.save(any()) } returns savedEntity
        coEvery { userRepository.findIdByUsername("my-user") } returns 199

        // when
        val result = persistentCampaignService.coInvokeInvisible<RunningCampaign>(
            "convertAndSaveCampaign",
            "my-tenant",
            "my-user",
            campaign,
            false
        )

        // then
        assertThat(result).isSameAs(runningCampaign)
        coVerifyOrder {
            campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(campaign))
            hook1.preCreate(refEq(campaign), refEq(runningCampaign))
            hook2.preCreate(refEq(campaign), refEq(runningCampaign))
            tenantRepository.findIdByReference("my-tenant")
            userRepository.findIdByUsername("my-user")
            campaignRepository.save(withArg {
                assertThat(it).all {
                    prop(CampaignEntity::key).isEqualTo("my-campaign")
                    prop(CampaignEntity::name).isEqualTo("This is a campaign")
                    prop(CampaignEntity::speedFactor).isEqualTo(123.2)
                    prop(CampaignEntity::scheduledMinions).isEqualTo(18593)
                    prop(CampaignEntity::hardTimeout).isNotNull().isEqualTo(Instant.MIN)
                    prop(CampaignEntity::softTimeout).isNotNull().isEqualTo(Instant.MIN)
                    prop(CampaignEntity::configurer).isEqualTo(199L)
                    prop(CampaignEntity::tenantId).isEqualTo(8165L)
                    prop(CampaignEntity::configuration).isSameAs(campaign)
                    prop(CampaignEntity::result).isEqualTo(QUEUED)
                }
            })
            campaignScenarioRepository.saveAll(
                listOf(
                    CampaignScenarioEntity(8126, "scenario-1", minionsCount = 6272),
                    CampaignScenarioEntity(8126, "scenario-2", minionsCount = 12321)
                )
            )
        }

        confirmVerified(
            userRepository,
            campaignRepository,
            tenantRepository,
            campaignScenarioRepository,
            campaignConfigurationConverter,
            campaignConverter,
            factoryRepository,
            campaignScheduler,
            factoryService,
            hook1,
            hook2
        )
    }

    @Test
    internal fun `should correctly handle the first hook exceptions when saving a campaign`() =
        testDispatcherProvider.run {
            // given
            val campaign = CampaignConfiguration(
                name = "This is a campaign",
                speedFactor = 123.2,
                scenarios = mapOf(
                    "scenario-1" to ScenarioRequest(1),
                    "scenario-2" to ScenarioRequest(3)
                )
            )
            val runningCampaign = relaxedMockk<RunningCampaign> {
                every { key } returns "my-campaign"
                every { scenarios } returns mapOf(
                    "scenario-1" to relaxedMockk { every { minionsCount } returns 6272 },
                    "scenario-2" to relaxedMockk { every { minionsCount } returns 12321 }
                )
            }
            coEvery {
                campaignConfigurationConverter.convertConfiguration(
                    "my-tenant",
                    refEq(campaign)
                )
            } returns runningCampaign
            coEvery {
                hook1.preCreate(refEq(campaign), refEq(runningCampaign))
            } throws BulkIllegalArgumentException(listOf("Constraints errors one", "Constraints errors two"))

            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                persistentCampaignService.coInvokeInvisible<RunningCampaign>(
                    "convertAndSaveCampaign",
                    "my-tenant",
                    "my-user",
                    campaign,
                    false
                )
            }

            // then
            assertThat(exception.messages.toList()).all {
                hasSize(2)
                index(0).isEqualTo("Constraints errors one")
                index(1).isEqualTo("Constraints errors two")
            }
            coVerifyOrder {
                campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(campaign))
                hook1.preCreate(refEq(campaign), refEq(runningCampaign))
            }

            confirmVerified(
                userRepository,
                campaignRepository,
                tenantRepository,
                campaignScenarioRepository,
                campaignConfigurationConverter,
                campaignConverter,
                factoryRepository,
                campaignScheduler,
                factoryService,
                hook1,
                hook2
            )
        }

    @Test
    internal fun `should stop immediately if the first hook raises exceptions when saving a campaign`() =
        testDispatcherProvider.run {
            // given
            val campaign = CampaignConfiguration(
                name = "This is a campaign",
                speedFactor = 123.2,
                scenarios = mapOf(
                    "scenario-1" to ScenarioRequest(1),
                    "scenario-2" to ScenarioRequest(3)
                )
            )
            val runningCampaign = relaxedMockk<RunningCampaign> {
                every { key } returns "my-campaign"
                every { scenarios } returns mapOf(
                    "scenario-1" to relaxedMockk { every { minionsCount } returns 6272 },
                    "scenario-2" to relaxedMockk { every { minionsCount } returns 12321 }
                )
            }
            coEvery {
                campaignConfigurationConverter.convertConfiguration(
                    "my-tenant",
                    refEq(campaign)
                )
            } returns runningCampaign
            coEvery {
                hook1.preCreate(refEq(campaign), refEq(runningCampaign))
            } throws BulkIllegalArgumentException(listOf("Constraints errors one", "Constraints errors two"))
            coEvery {
                hook2.preCreate(refEq(campaign), refEq(runningCampaign))
            } throws BulkIllegalArgumentException(listOf("Constraints errors three", "Constraints errors four"))

            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                persistentCampaignService.coInvokeInvisible<RunningCampaign>(
                    "convertAndSaveCampaign",
                    "my-tenant",
                    "my-user",
                    campaign,
                    false
                )
            }

            // then
            assertThat(exception.messages.toList()).all {
                hasSize(2)
                index(0).isEqualTo("Constraints errors one")
                index(1).isEqualTo("Constraints errors two")
            }
            coVerifyOrder {
                campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(campaign))
                hook1.preCreate(refEq(campaign), refEq(runningCampaign))
            }

            confirmVerified(
                userRepository,
                campaignRepository,
                tenantRepository,
                campaignScenarioRepository,
                campaignConfigurationConverter,
                campaignConverter,
                factoryRepository,
                campaignScheduler,
                factoryService,
                hook1,
                hook2
            )
        }

    @Test
    internal fun `should correctly handle any hook exception in provided order when saving a campaign`() =
        testDispatcherProvider.run {
            // given
            val campaign = CampaignConfiguration(
                name = "This is a campaign",
                speedFactor = 123.2,
                scenarios = mapOf(
                    "scenario-1" to ScenarioRequest(1),
                    "scenario-2" to ScenarioRequest(3)
                )
            )
            val runningCampaign = relaxedMockk<RunningCampaign> {
                every { key } returns "my-campaign"
                every { scenarios } returns mapOf(
                    "scenario-1" to relaxedMockk { every { minionsCount } returns 6272 },
                    "scenario-2" to relaxedMockk { every { minionsCount } returns 12321 }
                )
            }
            coEvery {
                campaignConfigurationConverter.convertConfiguration(
                    "my-tenant",
                    refEq(campaign)
                )
            } returns runningCampaign
            coEvery {
                hook2.preCreate(refEq(campaign), refEq(runningCampaign))
            } throws BulkIllegalArgumentException(listOf("Constraints errors three", "Constraints errors four"))

            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                persistentCampaignService.coInvokeInvisible<RunningCampaign>(
                    "convertAndSaveCampaign",
                    "my-tenant",
                    "my-user",
                    campaign,
                    false
                )
            }

            // then
            assertThat(exception.messages.toList()).all {
                hasSize(2)
                index(0).isEqualTo("Constraints errors three")
                index(1).isEqualTo("Constraints errors four")
            }
            coVerifyOrder {
                campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(campaign))
                hook1.preCreate(refEq(campaign), refEq(runningCampaign))
                hook2.preCreate(refEq(campaign), refEq(runningCampaign))
            }

            confirmVerified(
                userRepository,
                campaignRepository,
                tenantRepository,
                campaignScenarioRepository,
                campaignConfigurationConverter,
                campaignConverter,
                factoryRepository,
                campaignScheduler,
                factoryService,
                hook1,
                hook2
            )
        }

    @Test
    internal fun `should save a campaign during scheduling with timeout`() = testDispatcherProvider.run {
        // given
        val scheduleAt = Instant.now().plusSeconds(60)
        val configuration = CampaignConfiguration(
            name = "This is a campaign",
            speedFactor = 123.2,
            timeout = Duration.ofSeconds(715),
            hardTimeout = false,
            scenarios = mapOf(
                "scenario-1" to ScenarioRequest(1),
                "scenario-2" to ScenarioRequest(3)
            ),
            scheduledAt = scheduleAt
        )
        val runningCampaign = relaxedMockk<RunningCampaign> {
            every { key } returns "my-campaign"
            every { scenarios } returns mapOf(
                "scenario-1" to relaxedMockk { every { minionsCount } returns 6272 },
                "scenario-2" to relaxedMockk { every { minionsCount } returns 12321 }
            )
        }
        val savedEntity = relaxedMockk<CampaignEntity> {
            every { id } returns 8126
        }

        coEvery { tenantRepository.findIdByReference("my-tenant") } returns 8165L
        coEvery {
            campaignConfigurationConverter.convertConfiguration(
                "my-tenant",
                refEq(configuration)
            )
        } returns runningCampaign
        coEvery { campaignRepository.save(any()) } returns savedEntity
        coEvery { userRepository.findIdByUsername("my-user") } returns 199
        // when
        val result = persistentCampaignService.coInvokeInvisible<RunningCampaign>(
            "convertAndSaveCampaign",
            "my-tenant",
            "my-user",
            configuration,
            true
        )

        // then
        assertThat(result).isSameAs(runningCampaign)
        coVerifyOrder {
            campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(configuration))
            hook1.preSchedule(refEq(configuration), refEq(runningCampaign))
            hook2.preSchedule(refEq(configuration), refEq(runningCampaign))
            tenantRepository.findIdByReference("my-tenant")
            userRepository.findIdByUsername("my-user")
            campaignRepository.save(withArg {
                assertThat(it).all {
                    prop(CampaignEntity::key).isEqualTo("my-campaign")
                    prop(CampaignEntity::name).isEqualTo("This is a campaign")
                    prop(CampaignEntity::speedFactor).isEqualTo(123.2)
                    prop(CampaignEntity::scheduledMinions).isEqualTo(18593)
                    prop(CampaignEntity::hardTimeout).isNotNull().isEqualTo(Instant.MIN)
                    prop(CampaignEntity::softTimeout).isNotNull().isBetween(
                        (Instant.now() + (configuration.timeout?.minus(Duration.ofSeconds(1)))),
                        (Instant.now() + configuration.timeout)
                    )
                    prop(CampaignEntity::configurer).isEqualTo(199L)
                    prop(CampaignEntity::tenantId).isEqualTo(8165L)
                    prop(CampaignEntity::configuration).isSameAs(configuration)
                    prop(CampaignEntity::result).isEqualTo(SCHEDULED)
                }
            })
            campaignScenarioRepository.saveAll(
                listOf(
                    CampaignScenarioEntity(8126, "scenario-1", minionsCount = 6272),
                    CampaignScenarioEntity(8126, "scenario-2", minionsCount = 12321)
                )
            )
        }

        confirmVerified(
            userRepository,
            campaignRepository,
            tenantRepository,
            campaignScenarioRepository,
            campaignConfigurationConverter,
            campaignConverter,
            factoryRepository,
            campaignScheduler,
            factoryService,
            hook1,
            hook2
        )
    }

    @Test
    internal fun `should correctly handle the first hook exceptions when scheduling a campaign`() =
        testDispatcherProvider.run {
            // given
            val campaign = CampaignConfiguration(
                name = "This is a campaign",
                speedFactor = 123.2,
                scenarios = mapOf(
                    "scenario-1" to ScenarioRequest(1),
                    "scenario-2" to ScenarioRequest(3)
                )
            )
            val runningCampaign = relaxedMockk<RunningCampaign> {
                every { key } returns "my-campaign"
                every { scenarios } returns mapOf(
                    "scenario-1" to relaxedMockk { every { minionsCount } returns 6272 },
                    "scenario-2" to relaxedMockk { every { minionsCount } returns 12321 }
                )
            }
            coEvery {
                campaignConfigurationConverter.convertConfiguration(
                    "my-tenant",
                    refEq(campaign)
                )
            } returns runningCampaign
            coEvery {
                hook1.preSchedule(refEq(campaign), refEq(runningCampaign))
            } throws BulkIllegalArgumentException(listOf("Constraints errors one", "Constraints errors two"))

            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                persistentCampaignService.coInvokeInvisible<RunningCampaign>(
                    "convertAndSaveCampaign",
                    "my-tenant",
                    "my-user",
                    campaign,
                    true
                )
            }

            // then
            assertThat(exception.messages.toList()).all {
                hasSize(2)
                index(0).isEqualTo("Constraints errors one")
                index(1).isEqualTo("Constraints errors two")
            }
            coVerifyOrder {
                campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(campaign))
                hook1.preSchedule(refEq(campaign), refEq(runningCampaign))
            }

            confirmVerified(
                userRepository,
                campaignRepository,
                tenantRepository,
                campaignScenarioRepository,
                campaignConfigurationConverter,
                campaignConverter,
                factoryRepository,
                campaignScheduler,
                factoryService,
                hook1,
                hook2
            )
        }

    @Test
    internal fun `should stop immediately if the first hook raises exceptions when scheduling a campaign`() =
        testDispatcherProvider.run {
            // given
            val campaign = CampaignConfiguration(
                name = "This is a campaign",
                speedFactor = 123.2,
                scenarios = mapOf(
                    "scenario-1" to ScenarioRequest(1),
                    "scenario-2" to ScenarioRequest(3)
                )
            )
            val runningCampaign = relaxedMockk<RunningCampaign> {
                every { key } returns "my-campaign"
                every { scenarios } returns mapOf(
                    "scenario-1" to relaxedMockk { every { minionsCount } returns 6272 },
                    "scenario-2" to relaxedMockk { every { minionsCount } returns 12321 }
                )
            }
            coEvery {
                campaignConfigurationConverter.convertConfiguration(
                    "my-tenant",
                    refEq(campaign)
                )
            } returns runningCampaign
            coEvery {
                hook1.preSchedule(refEq(campaign), refEq(runningCampaign))
            } throws BulkIllegalArgumentException(listOf("Constraints errors one", "Constraints errors two"))
            coEvery {
                hook2.preSchedule(refEq(campaign), refEq(runningCampaign))
            } throws BulkIllegalArgumentException(listOf("Constraints errors three", "Constraints errors four"))

            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                persistentCampaignService.coInvokeInvisible<RunningCampaign>(
                    "convertAndSaveCampaign",
                    "my-tenant",
                    "my-user",
                    campaign,
                    true
                )
            }

            // then
            assertThat(exception.messages.toList()).all {
                hasSize(2)
                index(0).isEqualTo("Constraints errors one")
                index(1).isEqualTo("Constraints errors two")
            }
            coVerifyOrder {
                campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(campaign))
                hook1.preSchedule(refEq(campaign), refEq(runningCampaign))
            }

            confirmVerified(
                userRepository,
                campaignRepository,
                tenantRepository,
                campaignScenarioRepository,
                campaignConfigurationConverter,
                campaignConverter,
                factoryRepository,
                campaignScheduler,
                hook1,
                hook2
            )
        }

    @Test
    internal fun `should correctly handle any hook exception in provided order when scheduling a campaign`() =
        testDispatcherProvider.run {
            // given
            val campaign = CampaignConfiguration(
                name = "This is a campaign",
                speedFactor = 123.2,
                scenarios = mapOf(
                    "scenario-1" to ScenarioRequest(1),
                    "scenario-2" to ScenarioRequest(3)
                )
            )
            val runningCampaign = relaxedMockk<RunningCampaign> {
                every { key } returns "my-campaign"
                every { scenarios } returns mapOf(
                    "scenario-1" to relaxedMockk { every { minionsCount } returns 6272 },
                    "scenario-2" to relaxedMockk { every { minionsCount } returns 12321 }
                )
            }
            coEvery {
                campaignConfigurationConverter.convertConfiguration(
                    "my-tenant",
                    refEq(campaign)
                )
            } returns runningCampaign
            coEvery {
                hook2.preSchedule(refEq(campaign), refEq(runningCampaign))
            } throws BulkIllegalArgumentException(listOf("Constraints errors three", "Constraints errors four"))

            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                persistentCampaignService.coInvokeInvisible<RunningCampaign>(
                    "convertAndSaveCampaign",
                    "my-tenant",
                    "my-user",
                    campaign,
                    true
                )
            }

            // then
            assertThat(exception.messages.toList()).all {
                hasSize(2)
                index(0).isEqualTo("Constraints errors three")
                index(1).isEqualTo("Constraints errors four")
            }
            coVerifyOrder {
                campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(campaign))
                hook1.preSchedule(refEq(campaign), refEq(runningCampaign))
                hook2.preSchedule(refEq(campaign), refEq(runningCampaign))
            }

            confirmVerified(
                userRepository,
                campaignRepository,
                tenantRepository,
                campaignScenarioRepository,
                campaignConfigurationConverter,
                campaignConverter,
                factoryRepository,
                campaignScheduler,
                factoryService,
                hook1,
                hook2
            )
        }

}